/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.user.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.AuthzPermissionException;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.Edit;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.id.api.IdManager;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.SessionBindingEvent;
import org.sakaiproject.tool.api.SessionBindingListener;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.AuthenticatedUserProvider;
import org.sakaiproject.user.api.AuthenticationIdUDP;
import org.sakaiproject.user.api.AuthenticationManager;
import org.sakaiproject.user.api.ContextualUserDisplayService;
import org.sakaiproject.user.api.DisplayAdvisorUDP;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryProvider;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserFactory;
import org.sakaiproject.user.api.UserIdInvalidException;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;
import org.sakaiproject.user.api.UsersShareEmailUDP;
import org.sakaiproject.util.BaseResourceProperties;
import org.sakaiproject.util.BaseResourcePropertiesEdit;
import org.sakaiproject.util.StorageUser;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>
 * BaseUserDirectoryService is a Sakai user directory services implementation.
 * </p>
 * <p>
 * User records can be kept locally, in Sakai, externally, by a UserDirectoryProvider, or a mixture of both.
 * </p>
 * <p>
 * Each User that ever goes through Sakai is allocated a Sakai unique UUID. Even if we don't keep the User record in Sakai, we keep a map of this id to the external eid.
 * </p>
 */
public abstract class BaseUserDirectoryService implements UserDirectoryService, StorageUser, UserFactory
{
	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(BaseUserDirectoryService.class);

	/** Storage manager for this service. */
	protected Storage m_storage = null;

	/** The initial portion of a relative access point URL. */
	protected String m_relativeAccessPoint = null;

	/** An anon. user. */
	protected User m_anon = null;

	/** A user directory provider. */
	protected UserDirectoryProvider m_provider = null;

	/** Component ID used to find the provider if it's not directly injected. */
	protected String m_providerName = null;

	/** Key for current service caching of current user */
	protected final String M_curUserKey = getClass().getName() + ".currentUser";

	/** A cache of users */
	protected Cache m_callCache = null;
	
	/** Optional service to provide site-specific aliases for a user's display ID and display name. */
	protected ContextualUserDisplayService m_contextualUserDisplayService = null;
	
	/** Collaborator for doing passwords. */
	protected PasswordService m_pwdService = null;

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Abstractions, etc.
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Construct storage for this service.
	 */
	protected abstract Storage newStorage();

	/**
	 * Access the partial URL that forms the root of resource URLs.
	 *
	 * @param relative
	 *        if true, form within the access path only (i.e. starting with /content)
	 * @return the partial URL that forms the root of resource URLs.
	 */
	protected String getAccessPoint(boolean relative)
	{
		return (relative ? "" : serverConfigurationService().getAccessUrl()) + m_relativeAccessPoint;
	}

	/**
	 * Access the internal reference which can be used to access the resource from within the system.
	 *
	 * @param id
	 *        The user id string.
	 * @return The the internal reference which can be used to access the resource from within the system.
	 */
	public String userReference(String id)
	{
		// clean up the id
		id = cleanId(id);

		return getAccessPoint(true) + Entity.SEPARATOR + ((id == null) ? "" : id);
	}

	/**
	 * Access the user id extracted from a user reference.
	 *
	 * @param ref
	 *        The user reference string.
	 * @return The the user id extracted from a user reference.
	 */
	protected String userId(String ref)
	{
		String start = getAccessPoint(true) + Entity.SEPARATOR;
		int i = ref.indexOf(start);
		if (i == -1) return ref;
		String id = ref.substring(i + start.length());
		return id;
	}

	/**
	 * Check security permission.
	 *
	 * @param lock
	 *        The lock id string.
	 * @param resource
	 *        The resource reference string, or null if no resource is involved.
	 * @return true if allowd, false if not
	 */
	protected boolean unlockCheck(String lock, String resource)
	{
		if (!securityService().unlock(lock, resource))
		{
			return false;
		}

		return true;
	}

	/**
	 * Check security permission.
	 *
	 * @param lock
	 *        A list of lock strings to consider.
	 * @param resource
	 *        The resource reference string, or null if no resource is involved.
	 * @return true if any of these locks are allowed, false if not
	 */
	protected boolean unlockCheck(List locks, String resource)
	{
		Iterator locksIterator = locks.iterator();

		while(locksIterator.hasNext()) {

			if(securityService().unlock((String) locksIterator.next(), resource))
					return true;

		}

		return false;
	}

	/**
	 * Check security permission.
	 *
	 * @param lock1
	 *        The lock id string.
	 * @param lock2
	 *        The lock id string.
	 * @param resource
	 *        The resource reference string, or null if no resource is involved.
	 * @return true if either allowed, false if not
	 */
	protected boolean unlockCheck2(String lock1, String lock2, String resource)
	{
		if (!securityService().unlock(lock1, resource))
		{
			if (!securityService().unlock(lock2, resource))
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Check security permission.
	 *
	 * @param lock
	 *        The lock id string.
	 * @param resource
	 *        The resource reference string, or null if no resource is involved.
	 * @exception UserPermissionException
	 *            Thrown if the user does not have access
	 * @return The lock id string that succeeded
	 */
	protected String unlock(String lock, String resource) throws UserPermissionException
	{

		if (!unlockCheck(lock, resource))
		{
			throw new UserPermissionException(sessionManager().getCurrentSessionUserId(), lock, resource);
		}

	    return lock;
	}

	/**
	 * Check security permission.
	 *
	 * @param lock1
	 *        The lock id string.
	 * @param lock2
	 *        The lock id string.
	 * @param resource
	 *        The resource reference string, or null if no resource is involved.
	 * @exception UserPermissionException
	 *            Thrown if the user does not have access to either.
	 */
	protected void unlock2(String lock1, String lock2, String resource) throws UserPermissionException
	{
		if (!unlockCheck2(lock1, lock2, resource))
		{
			throw new UserPermissionException(sessionManager().getCurrentSessionUserId(), lock1 + "/" + lock2, resource);
		}
	}

	/**
	 * Check security permission.
	 *
	 *
	 * @param locks
	 *        The list of lock strings.
	 * @param resource
	 *        The resource reference string, or null if no resource is involved.
	 * @exception UserPermissionException
	 *            Thrown if the user does not have access to either.
	 * @return A list of the lock strings that the user has access to.
	 */

	protected List unlock(List locks, String resource) throws UserPermissionException
	{
		List locksSucceeded = new ArrayList();
		String locksFailed = "";

		Iterator locksIterator = locks.iterator();

		while (locksIterator.hasNext()) {

			String lock = (String) locksIterator.next();

			if (unlockCheck(lock, resource))
			{
				locksSucceeded.add(lock);

			} else {

				locksFailed += lock + " ";
			}

		}

		if (locksSucceeded.size() < 1) {
			throw new UserPermissionException(sessionManager().getCurrentSessionUserId(), locksFailed, resource);
		}

		return locksSucceeded;
	}

	/**
	 * Make sure we have a good uuid for a user record. If id is specified, use that. Otherwise get one from the provider or allocate a uuid.
	 *
	 * @param id
	 *        The proposed id.
	 * @param eid
	 *        The proposed eid.
	 * @return The id to use as the User's uuid.
	 */
	protected String assureUuid(String id, String eid)
	{
		// if we are not using separate id and eid, return the eid
		if (!m_separateIdEid) return eid;

		if (id != null) return id;

		// TODO: let the provider have a chance to set this? -ggolden

		// allocate a uuid
		id = idManager().createUuid();

		return id;
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Configuration
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Configuration: set the user directory provider helper service.
	 *
	 * @param provider
	 *        the user directory provider helper service.
	 */
	public void setProvider(UserDirectoryProvider provider)
	{
		m_provider = provider;
	}
	public void setProviderName(String userDirectoryProviderName)
	{
		m_providerName = StringUtil.trimToNull(userDirectoryProviderName);
	}

	public void setContextualUserDisplayService(ContextualUserDisplayService contextualUserDisplayService) {
		m_contextualUserDisplayService = contextualUserDisplayService;
	}

	/** The # seconds to cache gets. 0 disables the cache. */
	protected int m_cacheSeconds = 0;

	/**
	 * Set the # minutes to cache a get.
	 *
	 * @param time
	 *        The # minutes to cache a get (as an integer string).
	 */
	public void setCacheMinutes(String time)
	{
		m_cacheSeconds = Integer.parseInt(time) * 60;
	}

	/** The # seconds to cache gets. 0 disables the cache. */
	protected int m_cacheCleanerSeconds = 0;

	/**
	 * Set the # minutes between cache cleanings.
	 *
	 * @param time
	 *        The # minutes between cache cleanings. (as an integer string).
	 */
	public void setCacheCleanerMinutes(String time)
	{
		m_cacheCleanerSeconds = Integer.parseInt(time) * 60;
	}

	/** Configuration: case sensitive user eid. */
	protected boolean m_caseSensitiveEid = false;

	/**
	 * Configuration: case sensitive user eid
	 *
	 * @param value
	 *        The case sensitive user eid.
	 */
	public void setCaseSensitiveId(String value)
	{
		m_caseSensitiveEid = new Boolean(value).booleanValue();
	}

	/** Configuration: use a different id and eid for each record (otherwise make them the same value). */
	protected boolean m_separateIdEid = false;

	/**
	 * Configuration: to use a separate value for id and eid for each user record, or not.
	 *
	 * @param value
	 *        The separateIdEid setting.
	 */
	public void setSeparateIdEid(String value)
	{
		m_separateIdEid = new Boolean(value).booleanValue();
	}
	
	/**
	 * Configuration: set the password service to use.
	 * 
	 */
	public void setPasswordService(PasswordService pwdService)
	{
		m_pwdService = pwdService;
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Dependencies
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * @return the ServerConfigurationService collaborator.
	 */
	protected abstract ServerConfigurationService serverConfigurationService();

	/**
	 * @return the EntityManager collaborator.
	 */
	protected abstract EntityManager entityManager();

	/**
	 * @return the SecurityService collaborator.
	 */
	protected abstract SecurityService securityService();

	/**
	 * @return the FunctionManager collaborator.
	 */
	protected abstract FunctionManager functionManager();

	/**
	 * @return the SessionManager collaborator.
	 */
	protected abstract SessionManager sessionManager();

	/**
	 * @return the MemoryService collaborator.
	 */
	protected abstract MemoryService memoryService();

	/**
	 * @return the EventTrackingService collaborator.
	 */
	protected abstract EventTrackingService eventTrackingService();

	/**
	 * @return the ThreadLocalManager collaborator.
	 */
	protected abstract ThreadLocalManager threadLocalManager();

	/**
	 * @return the AuthzGroupService collaborator.
	 */
	protected abstract AuthzGroupService authzGroupService();

	/**
	 * @return the TimeService collaborator.
	 */
	protected abstract TimeService timeService();

	/**
	 * @return the IdManager collaborator.
	 */
	protected abstract IdManager idManager();

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Init and Destroy
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		try
		{
			m_relativeAccessPoint = REFERENCE_ROOT;

			// construct storage and read
			m_storage = newStorage();
			m_storage.open();

			// make an anon. user
			m_anon = new BaseUserEdit("");

			// <= 0 indicates no caching desired
			if (m_cacheSeconds > 0)
			{
				M_log.warn("cacheSeconds@org.sakaiproject.user.api.UserDirectoryService is no longer supported");
			}
			if (m_cacheCleanerSeconds > 0) {
				M_log.warn("cacheCleanerSeconds@org.sakaiproject.user.api.UserDirectoryService is no longer supported");
			}
			m_callCache = memoryService().newCache(
					"org.sakaiproject.user.api.UserDirectoryService.callCache",
					userReference(""));
			// register as an entity producer
			entityManager().registerEntityProducer(this, REFERENCE_ROOT);

			// register functions
			functionManager().registerFunction(SECURE_ADD_USER);
			functionManager().registerFunction(SECURE_REMOVE_USER);
			functionManager().registerFunction(SECURE_UPDATE_USER_OWN);
			functionManager().registerFunction(SECURE_UPDATE_USER_OWN_NAME);
			functionManager().registerFunction(SECURE_UPDATE_USER_OWN_EMAIL);
			functionManager().registerFunction(SECURE_UPDATE_USER_OWN_PASSWORD);
			functionManager().registerFunction(SECURE_UPDATE_USER_OWN_TYPE);
			functionManager().registerFunction(SECURE_UPDATE_USER_ANY);

			// if no provider was set, see if we can find one
			if ((m_provider == null) && (m_providerName != null))
			{
				m_provider = (UserDirectoryProvider) ComponentManager.get(m_providerName);
			}
			
			// Check for optional contextual user display service.
			if (m_contextualUserDisplayService == null)
			{
				m_contextualUserDisplayService = (ContextualUserDisplayService) ComponentManager.get(ContextualUserDisplayService.class);
			}
			
			// Fallback to the default password service.
			if (m_pwdService == null)
			{
				m_pwdService = new PasswordService();
			}

			M_log.info("init(): provider: " + ((m_provider == null) ? "none" : m_provider.getClass().getName())
					+ " separateIdEid: " + m_separateIdEid);
		}
		catch (Throwable t)
		{
			M_log.warn("init(): ", t);
		}
	}

	/**
	 * Returns to uninitialized state. You can use this method to release resources thet your Service allocated when Turbine shuts down.
	 */
	public void destroy()
	{
		m_storage.close();
		m_storage = null;
		m_provider = null;
		m_anon = null;

		M_log.info("destroy()");
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * UserDirectoryService implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * {@inheritDoc}
	 */
	public String getUserEid(String id) throws UserNotDefinedException
	{
		id = cleanId(id);

		// first, check our map
		String eid = m_storage.checkMapForEid(id);
		if (eid != null) return eid;

		throw new UserNotDefinedException(id);
	}

	/**
	 * {@inheritDoc}
	 */
	public String getUserId(String eid) throws UserNotDefinedException
	{
		eid = cleanEid(eid);

		// first, check our map
		String id = m_storage.checkMapForId(eid);
		if (id != null) return id;

		// Try the provider. - should we switch to Aid here?
		UserEdit user = getProvidedUserByEid(id, eid);
		if (user != null)
		{
			id = user.getId();
			putCachedUser(userReference(id), user);
			return id;
		}

		// not found
		throw new UserNotDefinedException(eid);
	}

	protected UserEdit getProvidedUserByEid(String id, String eid)
	{
		if (m_provider != null)
		{
			// make a new edit to hold the provider's info, hoping it will be filled in
			// Since the provider may actually want to fill in the user ID itself,
			// there's no point in us allocating a new user ID until after it returns.
			BaseUserEdit user = new BaseUserEdit(id, eid);

			// check with the provider
			if (m_provider.getUser(user))
			{
				ensureMappedIdForProvidedUser(user);
				return user;
			}
			else
			{
				return null;
			}
		}

		return null;
	}

	protected void ensureMappedIdForProvidedUser(UserEdit user)
	{
		if (user.getId() == null)
		{
			String eid = user.getEid();
			String id = assureUuid(null, eid);
			m_storage.putMap(id, eid);
			user.setId(id);
		}
	}

	protected void checkAndEnsureMappedIdForProvidedUser(UserEdit user)
	{
		if (user.getId() == null)
		{
			user.setId(m_storage.checkMapForId(user.getEid()));
			ensureMappedIdForProvidedUser(user);
		}
	}

	/**
	 * @inheritDoc
	 */
	public User getUser(String id) throws UserNotDefinedException
	{
		// clean up the id
		id = cleanId(id);

		if (id == null) throw new UserNotDefinedException("null");

		// see if we've done this already in this thread
		String ref = userReference(id);
		UserEdit user = getCachedUser(ref);
		if (user == null)
		{
			// find our user record, and use it if we have it
			user = m_storage.getById(id);

			// let the provider provide if needed
			if ((user == null) && (m_provider != null))
			{
				// we need the eid for the provider - if we can't find an eid, we throw UserNotDefinedException
				String eid = m_storage.checkMapForEid(id);
				if (eid != null)
				{
					// TODO Should we distinguish an obsolete user ID from an incorrect user ID?
					// An obsolete ID will have an associated EID but that EID won't be known to
					// the provider any longer.
					// An incorrect ID is not found at all.
					user = getProvidedUserByEid(id, eid);
				}
			}

			if (user != null)
			{
				putCachedUser(ref, user);
			}
		}

		// if not found
		if (user == null)
		{
			throw new UserNotDefinedException(id);
		}

		return user;
	}

	/**
	 * @inheritDoc
	 */
	public User getUserByEid(String eid) throws UserNotDefinedException
	{
		UserEdit user = null;

		// clean up the eid
		eid = cleanEid(eid);
		if (eid == null) throw new UserNotDefinedException("null");

		String id = m_storage.checkMapForId(eid);
		if (id != null)
		{
			user = getCachedUser(userReference(id));
			if (user != null)
			{
				return user;
			}
			user = m_storage.getById(id);
		}
		if (user == null)
		{
			user = getProvidedUserByEid(id, eid);
			if (user == null) throw new UserNotDefinedException(eid);
		}
		putCachedUser(userReference(user.getId()), user);

		return user;
	}

	/**
	 * @inheritDoc
	 */
	public User getUserByAid(String aid) throws UserNotDefinedException
	{
		if (serverConfigurationService().getBoolean("login.has.aid", false))
		{
			if (m_provider instanceof AuthenticationIdUDP)
			{
				UserEdit user = new BaseUserEdit();
				if (((AuthenticationIdUDP)m_provider).getUserbyAid(aid, user))
				{
					String id = m_storage.checkMapForId(user.getEid());
					user.setId(id);
					ensureMappedIdForProvidedUser(user);
					putCachedUser(user.getReference(), user);
					return user;
				}
				throw new UserNotDefinedException(aid);
			}
			else
			{
				M_log.warn("login.has.aid is true but UserDirectoryProvider doesn't implement AuthenticationIdUDP");
			}
		}
		return getUserByEid(aid);
	}

	/**
	 * @inheritDoc
	 */
	public List getUsers(Collection ids)
	{
		// Clean IDs to match the by-user case.
		Set<String> searchIds = new HashSet<String>();
		for (Iterator<Object> idIter = ids.iterator(); idIter.hasNext(); )
		{
			String id = (String)idIter.next();
			id = cleanEid(id);
			if (id != null) searchIds.add(id);
		}
		
		if (m_separateIdEid)
		{
			return m_storage.getUsersByIds(searchIds);
		}
		
		// Fall back to the old logic if this is a legacy system where 
		// "ID == EID", since that setting makes it difficult
		// to optimize while maintaining backwards compatibility: the user
		// record may be in the Sakai user table or not, and may be in the
		// EID-mapping table or not.
		
		// User objects to return
		List<UserEdit> rv = new Vector<UserEdit>();

		// a list of User (edits) setup to check with the provider
		Collection<UserEdit> fromProvider = new Vector<UserEdit>();

		// for each requested id
		for (String id : searchIds)
		{
			// see if we've done this already in this thread
			String ref = userReference(id);
			UserEdit user = getCachedUser(ref);
			if (user == null)
			{
				// find our user record
				user = m_storage.getById(id);
				if (user != null)
				{
					putCachedUser(ref, user);
				}
				else if (m_provider != null)
				{
					// get the eid for this user so we can ask the provider
					String eid = m_storage.checkMapForEid(id);
					if (eid != null)
					{
						// make a new edit to hold the provider's info; the provider will either fill this in, if known, or remove it from the collection
						fromProvider.add(new BaseUserEdit(id, eid));
					}
					else
					{
						// this user is not internally defined, and we can't find an eid for it, so we skip it
						M_log.warn("getUsers: cannot find eid for user id: " + id);
					}
				}
			}
			// add to return
			if (user != null) rv.add(user);
		}

		// check the provider, all at once
		if (!fromProvider.isEmpty())
		{
			m_provider.getUsers(fromProvider);

			// for each User in the collection that was filled in (and not removed) by the provider, cache and return it
			for (Iterator i = fromProvider.iterator(); i.hasNext();)
			{
				UserEdit user = (UserEdit) i.next();
				putCachedUser(user.getReference(), user);

				// add to return
				rv.add(user);
			}
		}

		return rv;
	}
	
	/**
	 * @see org.sakaiproject.user.api.UserDirectoryService#getUsersByEids(java.util.Collection)
	 */
	public List<User> getUsersByEids(Collection<String> eids)
	{
		if (!m_separateIdEid)
		{
			return getUsers(eids);
		}
		
		// Clean EIDs to match the by-user case.
		Set<String> searchEids = new HashSet<String>();
		for (String eid : eids)
		{
			eid = cleanEid(eid);
			if (eid != null) searchEids.add(eid);
		}
		
		return m_storage.getUsersByEids(searchEids);
	}

	/**
	 * @inheritDoc
	 */
	public User getCurrentUser()
	{
		String id = sessionManager().getCurrentSessionUserId();

		// check current service caching - discard if the session user is different
		User rv = (User) threadLocalManager().get(M_curUserKey);
		if ((rv != null) && (rv.getId().equals(id))) return rv;

		try
		{
			rv = getUser(id);
		}
		catch (UserNotDefinedException e)
		{
			rv = getAnonymousUser();
		}

		// cache in the current service
		threadLocalManager().set(M_curUserKey, rv);

		return rv;
	}

	/**
	 * @inheritDoc
	 */
	public boolean allowUpdateUser(String id)
	{
		// clean up the id
		id = cleanId(id);
		if (id == null) return false;

		// is this the user's own?
		if (id.equals(sessionManager().getCurrentSessionUserId()))
		{
			ArrayList locks = new ArrayList();
			locks.add(SECURE_UPDATE_USER_OWN);
			locks.add(SECURE_UPDATE_USER_ANY);
			locks.add(SECURE_UPDATE_USER_OWN_NAME);
			locks.add(SECURE_UPDATE_USER_OWN_EMAIL);
			locks.add(SECURE_UPDATE_USER_OWN_PASSWORD);
			locks.add(SECURE_UPDATE_USER_OWN_TYPE);

			// own or any
			return unlockCheck(locks, userReference(id));
		}

		else
		{
			// just any
			return unlockCheck(SECURE_UPDATE_USER_ANY, userReference(id));
		}
	}

	/**
	 * @inheritDoc
	 */
	public boolean allowUpdateUserName(String id)
	{
		// clean up the id
		id = cleanId(id);
		if (id == null) return false;

		//		 is this the user's own?
		if (id.equals(sessionManager().getCurrentSessionUserId()))
		{
			ArrayList locks = new ArrayList();
			locks.add(SECURE_UPDATE_USER_OWN);
			locks.add(SECURE_UPDATE_USER_ANY);
			locks.add(SECURE_UPDATE_USER_OWN_NAME);


			// own or any
			return unlockCheck(locks, userReference(id));
		}

		else
		{
			// just any
			return unlockCheck(SECURE_UPDATE_USER_ANY, userReference(id));
		}

	}

	/**
	 * @inheritDoc
	 */
	public boolean allowUpdateUserEmail(String id)
	{
		// clean up the id
		id = cleanId(id);
		if (id == null) return false;

		//		 is this the user's own?
		if (id.equals(sessionManager().getCurrentSessionUserId()))
		{
			ArrayList locks = new ArrayList();
			locks.add(SECURE_UPDATE_USER_OWN);
			locks.add(SECURE_UPDATE_USER_ANY);
			locks.add(SECURE_UPDATE_USER_OWN_EMAIL);


			// own or any
			return unlockCheck(locks, userReference(id));
		}

		else
		{
			// just any
			return unlockCheck(SECURE_UPDATE_USER_ANY, userReference(id));
		}

	}

	/**
	 * @inheritDoc
	 */
	public boolean allowUpdateUserPassword(String id)
	{
		// clean up the id
		id = cleanId(id);
		if (id == null) return false;

		//		 is this the user's own?
		if (id.equals(sessionManager().getCurrentSessionUserId()))
		{
			ArrayList locks = new ArrayList();
			locks.add(SECURE_UPDATE_USER_OWN);
			locks.add(SECURE_UPDATE_USER_ANY);
			locks.add(SECURE_UPDATE_USER_OWN_PASSWORD);


			// own or any
			return unlockCheck(locks, userReference(id));
		}

		else
		{
			// just any
			return unlockCheck(SECURE_UPDATE_USER_ANY, userReference(id));
		}

	}


	/**
	 * @inheritDoc
	 */
	public boolean allowUpdateUserType(String id)
	{
		// clean up the id
		id = cleanId(id);
		if (id == null) return false;

		//		 is this the user's own?
		if (id.equals(sessionManager().getCurrentSessionUserId()))
		{
			ArrayList locks = new ArrayList();
			locks.add(SECURE_UPDATE_USER_OWN);
			locks.add(SECURE_UPDATE_USER_ANY);
			locks.add(SECURE_UPDATE_USER_OWN_TYPE);


			// own or any
			return unlockCheck(locks, userReference(id));
		}

		else
		{
			// just any
			return unlockCheck(SECURE_UPDATE_USER_ANY, userReference(id));
		}

	}

	/**
	 * @inheritDoc
	 */
	public UserEdit editUser(String id) throws UserNotDefinedException, UserPermissionException, UserLockedException
	{
		// clean up the id
		id = cleanId(id);

		if (id == null) throw new UserNotDefinedException("null");

		// is this the user's own?
		List locksSucceeded = new ArrayList();
		String function = null;
		if (id.equals(sessionManager().getCurrentSessionUserId()))
		{
			// own or any
			List locks = new ArrayList();
			locks.add(SECURE_UPDATE_USER_OWN);
			locks.add(SECURE_UPDATE_USER_OWN_NAME);
			locks.add(SECURE_UPDATE_USER_OWN_EMAIL);
			locks.add(SECURE_UPDATE_USER_OWN_PASSWORD);
			locks.add(SECURE_UPDATE_USER_OWN_TYPE);
			locks.add(SECURE_UPDATE_USER_ANY);

			locksSucceeded = unlock(locks, userReference(id));
			function = SECURE_UPDATE_USER_OWN;
		}
		else
		{
			// just any
			locksSucceeded.add(unlock(SECURE_UPDATE_USER_ANY, userReference(id)));
			function = SECURE_UPDATE_USER_ANY;
		}

		// ignore the cache - get the user with a lock from the info store
		UserEdit user = m_storage.edit(id);
		if (user == null)
		{
			// Figure out which exception to throw.
			if (!m_storage.check(id))
			{
				throw new UserNotDefinedException(id);
			}
			else
			{
				throw new UserLockedException(id);
			}
		}

		if(!locksSucceeded.contains(SECURE_UPDATE_USER_ANY) && !locksSucceeded.contains(SECURE_UPDATE_USER_OWN)) {

			// current session does not have permission to edit all properties for this user
			// lock the properties the user does not have access to edit

			if(!locksSucceeded.contains(SECURE_UPDATE_USER_OWN_NAME)) {
				user.restrictEditFirstName();
			    user.restrictEditLastName();
			}

			if(!locksSucceeded.contains(SECURE_UPDATE_USER_OWN_EMAIL)) {
				user.restrictEditEmail();
			}

			if(!locksSucceeded.contains(SECURE_UPDATE_USER_OWN_PASSWORD)) {
				user.restrictEditPassword();
			}

			if(!locksSucceeded.contains(SECURE_UPDATE_USER_OWN_TYPE)) {
				user.restrictEditType();
			}

		}

		((BaseUserEdit) user).setEvent(function);

		return user;
	}

	/**
	 * @inheritDoc
	 */
	public void commitEdit(UserEdit user) throws UserAlreadyDefinedException
	{
		// check for closed edit
		if (!user.isActiveEdit())
		{
			M_log.warn("commitEdit(): closed UserEdit", new Exception());
			return;
		}

		// update the properties
		addLiveUpdateProperties((BaseUserEdit) user);

		// complete the edit
		if (!m_storage.commit(user))
		{
			m_storage.cancel(user);
			((BaseUserEdit) user).closeEdit();
			throw new UserAlreadyDefinedException(user.getEid());
		}

		String ref = user.getReference();

		// track it
		eventTrackingService().post(eventTrackingService().newEvent(((BaseUserEdit) user).getEvent(), ref, true));

		// close the edit object
		((BaseUserEdit) user).closeEdit();

		// Update the caches to match any changed data.
		putCachedUser(ref, user);
	}

	/**
	 * @inheritDoc
	 */
	public void cancelEdit(UserEdit user)
	{
		// check for closed edit
		if (!user.isActiveEdit())
		{
			try
			{
				throw new Exception();
			}
			catch (Exception e)
			{
				M_log.warn("cancelEdit(): closed UserEdit", e);
			}
			return;
		}

		// release the edit lock
		m_storage.cancel(user);

		// close the edit object
		((BaseUserEdit) user).closeEdit();
	}

	/**
	 * @inheritDoc
	 */
	public List getUsers()
	{
		List users = m_storage.getAll();
		return users;
	}

	/**
	 * @inheritDoc
	 */
	public List getUsers(int first, int last)
	{
		List all = m_storage.getAll(first, last);

		return all;
	}

	/**
	 * @inheritDoc
	 */
	public int countUsers()
	{
		return m_storage.count();
	}

	/**
	 * @inheritDoc
	 */
	public List searchUsers(String criteria, int first, int last)
	{
		return m_storage.search(criteria, first, last);
	}

	/**
	 * @inheritDoc
	 */
	public int countSearchUsers(String criteria)
	{
		return m_storage.countSearch(criteria);
	}

	/**
	 * @inheritDoc
	 */
	@SuppressWarnings("unchecked")
	public Collection findUsersByEmail(String email)
	{
		Collection users = new ArrayList();

		// add in provider users
		if (m_provider != null)
		{
			Collection<BaseUserEdit> providedUserRecords = null;

			// support UDP that has multiple users per email
			if (m_provider instanceof UsersShareEmailUDP)
			{
				providedUserRecords = ((UsersShareEmailUDP) m_provider).findUsersByEmail(email, this);
			}
			else
			{
				// make a new edit to hold the provider's info
				BaseUserEdit edit = new BaseUserEdit();
				if (m_provider.findUserByEmail(edit, email))
				{
					providedUserRecords = Arrays.asList(new BaseUserEdit[] {edit});
				}
			}

			if (providedUserRecords != null)
			{
				for (BaseUserEdit user : providedUserRecords)
				{
					checkAndEnsureMappedIdForProvidedUser(user);
					users.add(user);
				}
			}
		}

		// Append any matching internal users.
		users.addAll(m_storage.findUsersByEmail(email));
		
		return users;
	}

	/**
	 * @inheritDoc
	 */
	public User getAnonymousUser()
	{
		return m_anon;
	}

	/**
	 * @inheritDoc
	 */
	public boolean allowAddUser()
	{
		return unlockCheck(SECURE_ADD_USER, userReference(""));
	}

	/**
	 * @inheritDoc
	 */
	public UserEdit addUser(String id, String eid) throws UserIdInvalidException, UserAlreadyDefinedException,
			UserPermissionException
	{
		// clean the ids
		id = cleanId(id);
		eid = cleanEid(eid);

		// make sure we have an id
		id = assureUuid(id, eid);

		// check security (throws if not permitted)
		unlock(SECURE_ADD_USER, userReference(id));

		// reserve a user with this id from the info store - if it's in use, this will return null
		UserEdit user = m_storage.put(id, eid);
		if (user == null)
		{
			throw new UserAlreadyDefinedException(id + " -" + eid);
		}

		((BaseUserEdit) user).setEvent(SECURE_ADD_USER);

		return user;
	}

	/**
	 * @inheritDoc
	 */
	public User addUser(String id, String eid, String firstName, String lastName, String email, String pw, String type,
			ResourceProperties properties) throws UserIdInvalidException, UserAlreadyDefinedException, UserPermissionException
	{
		// get it added
		UserEdit edit = addUser(id, eid);

		// fill in the fields
		edit.setLastName(lastName);
		edit.setFirstName(firstName);
		edit.setEmail(email);
		edit.setPassword(pw);
		edit.setType(type);

		ResourcePropertiesEdit props = edit.getPropertiesEdit();
		if (properties != null)
		{
			props.addAll(properties);
		}

		// no live props!

		// get it committed - no further security check
		if (!m_storage.commit(edit))
		{
			m_storage.cancel(edit);
			((BaseUserEdit) edit).closeEdit();
			throw new UserAlreadyDefinedException(edit.getEid());
		}

		// track it
		eventTrackingService().post(eventTrackingService().newEvent(((BaseUserEdit) edit).getEvent(), edit.getReference(), true));

		// close the edit object
		((BaseUserEdit) edit).closeEdit();

		return edit;
	}

	/**
	 * @inheritDoc
	 */
	public UserEdit mergeUser(Element el) throws UserIdInvalidException, UserAlreadyDefinedException, UserPermissionException
	{
		// construct from the XML
		User userFromXml = new BaseUserEdit(el);

		// check for a valid eid
		Validator.checkResourceId(userFromXml.getEid());

		// check security (throws if not permitted)
		unlock(SECURE_ADD_USER, userFromXml.getReference());

		// reserve a user with this id from the info store - if it's in use, this will return null
		UserEdit user = m_storage.put(userFromXml.getId(), userFromXml.getEid());
		if (user == null)
		{
			throw new UserAlreadyDefinedException(userFromXml.getId() + " - " + userFromXml.getEid());
		}

		// transfer from the XML read user object to the UserEdit
		((BaseUserEdit) user).set(userFromXml);

		((BaseUserEdit) user).setEvent(SECURE_ADD_USER);

		return user;
	}

	/**
	 * @inheritDoc
	 */
	public boolean allowRemoveUser(String id)
	{
		// clean up the id
		id = cleanId(id);
		if (id == null) return false;

		return unlockCheck(SECURE_REMOVE_USER, userReference(id));
	}

	/**
	 * @inheritDoc
	 */
	public void removeUser(UserEdit user) throws UserPermissionException
	{
		String ref = user.getReference();

		// check for closed edit
		if (!user.isActiveEdit())
		{
			M_log.warn("removeUser(): closed UserEdit", new Exception());
			return;
		}

		// check security (throws if not permitted)
		unlock(SECURE_REMOVE_USER, ref);

		// complete the edit
		m_storage.remove(user);

		// track it
		eventTrackingService().post(eventTrackingService().newEvent(SECURE_REMOVE_USER, ref, true));

		// close the edit object
		((BaseUserEdit) user).closeEdit();

		// remove any realm defined for this resource
		try
		{
			authzGroupService().removeAuthzGroup(authzGroupService().getAuthzGroup(ref));
		}
		catch (AuthzPermissionException e)
		{
			M_log.warn("removeUser: removing realm for : " + ref + " : " + e);
		}
		catch (GroupNotDefinedException ignore)
		{
		}

		// Remove from cache.
		removeCachedUser(ref);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <b>WARNING:</b> Do not call this method directly! Use {@link AuthenticationManager#authenticate(org.sakaiproject.user.api.Evidence)}
	 */
	public User authenticate(String loginId, String password)
	{
		loginId = StringUtil.trimToNull(loginId);
		if (loginId == null) return null;

		UserEdit user = null;
		boolean authenticateWithProviderFirst = (m_provider != null) && m_provider.authenticateWithProviderFirst(loginId);

		if (authenticateWithProviderFirst)
		{
			user = getProviderAuthenticatedUser(loginId, password);
			if (user != null) return user;
		}

		user = getInternallyAuthenticatedUser(loginId, password);
		if (user != null) return user;

		if ((m_provider != null) && !authenticateWithProviderFirst)
		{
			return getProviderAuthenticatedUser(loginId, password);
		}

		return null;
	}

	protected UserEdit getInternallyAuthenticatedUser(String eid, String password)
	{
		try
		{
			UserEdit user = (UserEdit)getUserByEid(eid);
			return user.checkPassword(password) ? user : null;
		} catch (UserNotDefinedException e)
		{
			// Give up and possibly pass along to another authentication service.
			return null;
		}
	}

	protected UserEdit getProviderAuthenticatedUser(String loginId, String password)
	{
		UserEdit user = null;
		if (m_provider instanceof AuthenticatedUserProvider)
		{
			// Since the login ID might differ from the EID, the provider is in charge
			// of filling in user data as well as authenticating the user.
			user = ((AuthenticatedUserProvider)m_provider).getAuthenticatedUser(loginId, password);
		}
		else
		{
			// The pre-2.5 authenticateUser method was ambiguous due to the lack of a
			// distinct "AuthenticationProvider" and the inability to indicate "emptiness"
			// in a UserEdit record. Here are the revised options:
			//
			// 1) If the provider is basically authentication-only, then this logic will find
			// the locally stored user record and pass it on.
			//
			// 2) If the provider handles both data provision and authentication, then
			// this logic will find the provided user data and pass it on.
			//
			// 3) If the provider needs to authenticate a user using a login ID
			// that does not match the EID of an already locally stored user record or a
			// provided user, then the provider should be changed to implement the
			// AuthenticatedUserProvider interface.
			//
			// Note that this legacy interface does not allow EIDs and login IDs to differ,
			// and this logic will therefore not attempt to authenticate a user unless
			// "getUserByEid(loginId)" returns success. This preserves backwards
			// compatibility for legacy providers that perform authentication only for
			// locally stored users.
			try
			{
				user = (UserEdit)getUserByAid(loginId);
			} catch (UserNotDefinedException e)
			{
				return null;
			}
			boolean authenticated = m_provider.authenticateUser(loginId, user, password);
			if (!authenticated) user = null;
		}
		if (user != null)
		{
			checkAndEnsureMappedIdForProvidedUser(user);
			putCachedUser(user.getReference(), user);
			return user;
		}
		return null;
	}


	/**
	 * @inheritDoc
	 */
	public void destroyAuthentication()
	{
	}

	/**
	 * Create the live properties for the user.
	 */
	protected void addLiveProperties(BaseUserEdit edit)
	{
		String current = sessionManager().getCurrentSessionUserId();

		edit.m_createdUserId = current;
		edit.m_lastModifiedUserId = current;

		Time now = timeService().newTime();
		edit.m_createdTime = now;
		edit.m_lastModifiedTime = (Time) now.clone();
	}

	/**
	 * Update the live properties for a user for when modified.
	 */
	protected void addLiveUpdateProperties(BaseUserEdit edit)
	{
		String current = sessionManager().getCurrentSessionUserId();

		edit.m_lastModifiedUserId = current;
		edit.m_lastModifiedTime = timeService().newTime();
	}

	/**
	 * Adjust the id - trim it to null. Note: eid case insensitive option does NOT apply to id.
	 *
	 * @param id
	 *        The id to clean up.
	 * @return A cleaned up id.
	 */
	protected String cleanId(String id)
	{
		// if we are not doing separate id and eid, use the eid rules
		if (!m_separateIdEid) return cleanEid(id);

		return StringUtil.trimToNull(id);
	}

	/**
	 * Adjust the eid - trim it to null, and lower case IF we are case insensitive.
	 *
	 * @param eid
	 *        The eid to clean up.
	 * @return A cleaned up eid.
	 */
	protected String cleanEid(String eid)
	{
		if (!m_caseSensitiveEid)
		{
			return StringUtil.trimToNullLower(eid);
		}

		return StringUtil.trimToNull(eid);
	}

	protected UserEdit getCachedUser(String ref)
	{
		UserEdit user = (UserEdit)threadLocalManager().get(ref);
		if ((user == null) && (m_callCache != null))
		{
			user = (UserEdit)m_callCache.get(ref);
		}
		return user;
	}

	protected void putCachedUser(String ref, UserEdit user)
	{
		threadLocalManager().set(ref, user);
		if (m_callCache != null) m_callCache.put(ref, user, m_cacheSeconds);
	}

	protected void removeCachedUser(String ref)
	{
		threadLocalManager().set(ref, null);
		if (m_callCache != null) m_callCache.remove(ref);
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * EntityProducer implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * @inheritDoc
	 */
	public String getLabel()
	{
		return "user";
	}

	/**
	 * @inheritDoc
	 */
	public boolean willArchiveMerge()
	{
		return false;
	}

	/**
	 * @inheritDoc
	 */
	public HttpAccess getHttpAccess()
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public boolean parseEntityReference(String reference, Reference ref)
	{
		// for user access
		if (reference.startsWith(REFERENCE_ROOT))
		{
			String id = null;

			// we will get null, service, userId
			String[] parts = StringUtil.split(reference, Entity.SEPARATOR);

			if (parts.length > 2)
			{
				id = parts[2];
			}

			ref.set(APPLICATION_ID, null, id, null, null);

			return true;
		}

		return false;
	}

	/**
	 * @inheritDoc
	 */
	public String getEntityDescription(Reference ref)
	{
		// double check that it's mine
		if (APPLICATION_ID != ref.getType()) return null;

		String rv = "User: " + ref.getReference();

		try
		{
			User user = getUser(ref.getId());
			rv = "User: " + user.getDisplayName();
		}
		catch (UserNotDefinedException e)
		{
		}
		catch (NullPointerException e)
		{
		}

		return rv;
	}

	/**
	 * @inheritDoc
	 */
	public ResourceProperties getEntityResourceProperties(Reference ref)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Entity getEntity(Reference ref)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Collection getEntityAuthzGroups(Reference ref, String userId)
	{
		// double check that it's mine
		if (APPLICATION_ID != ref.getType()) return null;

		Collection rv = new Vector();

		// for user access: user and template realms
		try
		{
			rv.add(userReference(ref.getId()));

			ref.addUserTemplateAuthzGroup(rv, userId);
		}
		catch (NullPointerException e)
		{
			M_log.warn("getEntityRealms(): " + e);
		}

		return rv;
	}

	/**
	 * @inheritDoc
	 */
	public String getEntityUrl(Reference ref)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public String archive(String siteId, Document doc, Stack stack, String archivePath, List attachments)
	{
		return "";
	}

	/**
	 * @inheritDoc
	 */
	public String merge(String siteId, Element root, String archivePath, String fromSiteId, Map attachmentNames, Map userIdTrans,
			Set userListAllowImport)
	{
		return "";
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * UserFactory implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * @inheritDoc
	 */
	public UserEdit newUser()
	{
		return new BaseUserEdit();
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * UserEdit implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * <p>
	 * BaseUserEdit is an implementation of the UserEdit object.
	 * </p>
	 */
	public class BaseUserEdit implements UserEdit, SessionBindingListener
	{
		/** The event code for this edit. */
		protected String m_event = null;

		/** Active flag. */
		protected boolean m_active = false;

		/** The user id. */
		protected String m_id = null;

		/** The user eid. */
		protected String m_eid = null;

		/** The user first name. */
		protected String m_firstName = null;

		/** The user last name. */
		protected String m_lastName = null;

		/** The user email address. */
		protected String m_email = null;

		/** The user password. */
		protected String m_pw = null;

		/** The properties. */
		protected ResourcePropertiesEdit m_properties = null;

		/** The user type. */
		protected String m_type = null;

		/** The created user id. */
		protected String m_createdUserId = null;

		/** The last modified user id. */
		protected String m_lastModifiedUserId = null;

		/** The time created. */
		protected Time m_createdTime = null;

		/** The time last modified. */
		protected Time m_lastModifiedTime = null;

		/** If editing the first name is restricted **/
		protected boolean m_restrictedFirstName = false;

		/** If editing the last name is restricted **/
		protected boolean m_restrictedLastName = false;


		/** If editing the email is restricted **/
		protected boolean m_restrictedEmail = false;

		/** If editing the password is restricted **/
		protected boolean m_restrictedPassword = false;

		/** If editing the type is restricted **/
		protected boolean m_restrictedType = false;

		// in object cache of the sort name.
		private transient String m_sortName;

		/**
		 * Construct.
		 *
		 * @param id
		 *        The user id.
		 */
		public BaseUserEdit(String id, String eid)
		{
			m_id = id;
			m_eid = eid;

			// setup for properties
			ResourcePropertiesEdit props = new BaseResourcePropertiesEdit();
			m_properties = props;

			// if the id is not null (a new user, rather than a reconstruction)
			// and not the anon (id == "") user,
			// add the automatic (live) properties
			if ((m_id != null) && (m_id.length() > 0)) addLiveProperties(this);
		}

		public BaseUserEdit(String id)
		{
			this(id, null);
		}

		public BaseUserEdit()
		{
			this(null, null);
		}

		/**
		 * Construct from another User object.
		 *
		 * @param user
		 *        The user object to use for values.
		 */
		public BaseUserEdit(User user)
		{
			setAll(user);
		}

		/**
		 * Construct from information in XML.
		 *
		 * @param el
		 *        The XML DOM Element definining the user.
		 */
		public BaseUserEdit(Element el)
		{
			// setup for properties
			m_properties = new BaseResourcePropertiesEdit();

			m_id = cleanId(el.getAttribute("id"));
			m_eid = cleanEid(el.getAttribute("eid"));
			m_firstName = StringUtil.trimToNull(el.getAttribute("first-name"));
			m_lastName = StringUtil.trimToNull(el.getAttribute("last-name"));
			setEmail(StringUtil.trimToNull(el.getAttribute("email")));
			m_pw = el.getAttribute("pw");
			m_type = StringUtil.trimToNull(el.getAttribute("type"));
			m_createdUserId = StringUtil.trimToNull(el.getAttribute("created-id"));
			m_lastModifiedUserId = StringUtil.trimToNull(el.getAttribute("modified-id"));

			String time = StringUtil.trimToNull(el.getAttribute("created-time"));
			if (time != null)
			{
				m_createdTime = timeService().newTimeGmt(time);
			}

			time = StringUtil.trimToNull(el.getAttribute("modified-time"));
			if (time != null)
			{
				m_lastModifiedTime = timeService().newTimeGmt(time);
			}

			// the children (roles, properties)
			NodeList children = el.getChildNodes();
			final int length = children.getLength();
			for (int i = 0; i < length; i++)
			{
				Node child = children.item(i);
				if (child.getNodeType() != Node.ELEMENT_NODE) continue;
				Element element = (Element) child;

				// look for properties
				if (element.getTagName().equals("properties"))
				{
					// re-create properties
					m_properties = new BaseResourcePropertiesEdit(element);

					// pull out some properties into fields to convert old (pre 1.38) versions
					if (m_createdUserId == null)
					{
						m_createdUserId = m_properties.getProperty("CHEF:creator");
					}
					if (m_lastModifiedUserId == null)
					{
						m_lastModifiedUserId = m_properties.getProperty("CHEF:modifiedby");
					}
					if (m_createdTime == null)
					{
						try
						{
							m_createdTime = m_properties.getTimeProperty("DAV:creationdate");
						}
						catch (Exception ignore)
						{
						}
					}
					if (m_lastModifiedTime == null)
					{
						try
						{
							m_lastModifiedTime = m_properties.getTimeProperty("DAV:getlastmodified");
						}
						catch (Exception ignore)
						{
						}
					}
					m_properties.removeProperty("CHEF:creator");
					m_properties.removeProperty("CHEF:modifiedby");
					m_properties.removeProperty("DAV:creationdate");
					m_properties.removeProperty("DAV:getlastmodified");
				}
			}
		}

		/**
		 * ReConstruct.
		 *
		 * @param id
		 *        The id.
		 * @param eid
		 *        The eid.
		 * @param email
		 *        The email.
		 * @param firstName
		 *        The first name.
		 * @param lastName
		 *        The last name.
		 * @param type
		 *        The type.
		 * @param pw
		 *        The password.
		 * @param createdBy
		 *        The createdBy property.
		 * @param createdOn
		 *        The createdOn property.
		 * @param modifiedBy
		 *        The modified by property.
		 * @param modifiedOn
		 *        The modified on property.
		 */
		public BaseUserEdit(String id, String eid, String email, String firstName, String lastName, String type, String pw,
				String createdBy, Time createdOn, String modifiedBy, Time modifiedOn)
		{
			m_id = id;
			m_eid = eid;
			m_firstName = firstName;
			m_lastName = lastName;
			m_type = type;
			setEmail(email);
			m_pw = pw;
			m_createdUserId = createdBy;
			m_lastModifiedUserId = modifiedBy;
			m_createdTime = createdOn;
			m_lastModifiedTime = modifiedOn;

			// setup for properties, but mark them lazy since we have not yet established them from data
			BaseResourcePropertiesEdit props = new BaseResourcePropertiesEdit();
			props.setLazy(true);
			m_properties = props;
		}

		/**
		 * Take all values from this object.
		 *
		 * @param user
		 *        The user object to take values from.
		 */
		protected void setAll(User user)
		{
			m_id = user.getId();
			m_eid = user.getEid();
			m_firstName = user.getFirstName();
			m_lastName = user.getLastName();
			m_type = user.getType();
			setEmail(user.getEmail());
			m_pw = ((BaseUserEdit) user).m_pw;
			m_createdUserId = ((BaseUserEdit) user).m_createdUserId;
			m_lastModifiedUserId = ((BaseUserEdit) user).m_lastModifiedUserId;
			if (((BaseUserEdit) user).m_createdTime != null) m_createdTime = (Time) ((BaseUserEdit) user).m_createdTime.clone();
			if (((BaseUserEdit) user).m_lastModifiedTime != null)
				m_lastModifiedTime = (Time) ((BaseUserEdit) user).m_lastModifiedTime.clone();

			m_properties = new BaseResourcePropertiesEdit();
			m_properties.addAll(user.getProperties());
			((BaseResourcePropertiesEdit) m_properties).setLazy(((BaseResourceProperties) user.getProperties()).isLazy());
		}

		/**
		 * @inheritDoc
		 */
		public Element toXml(Document doc, Stack stack)
		{
			Element user = doc.createElement("user");

			if (stack.isEmpty())
			{
				doc.appendChild(user);
			}
			else
			{
				((Element) stack.peek()).appendChild(user);
			}

			stack.push(user);

			user.setAttribute("id", getId());
			user.setAttribute("eid", getEid());
			if (m_firstName != null) user.setAttribute("first-name", m_firstName);
			if (m_lastName != null) user.setAttribute("last-name", m_lastName);
			if (m_type != null) user.setAttribute("type", m_type);
			user.setAttribute("email", getEmail());
			user.setAttribute("created-id", m_createdUserId);
			user.setAttribute("modified-id", m_lastModifiedUserId);
			user.setAttribute("created-time", m_createdTime.toString());
			user.setAttribute("modified-time", m_lastModifiedTime.toString());

			// properties
			getProperties().toXml(doc, stack);

			stack.pop();

			return user;
		}

		/**
		 * @inheritDoc
		 */
		public String getId()
		{
			return m_id;
		}

		/**
		 * @inheritDoc
		 */
		public String getEid()
		{
			return m_eid;
		}

		/**
		 * @inheritDoc
		 */
		public String getUrl()
		{
			return getAccessPoint(false) + m_id;
		}

		/**
		 * @inheritDoc
		 */
		public String getReference()
		{
			return userReference(m_id);
		}

		/**
		 * @inheritDoc
		 */
		public String getReference(String rootProperty)
		{
			return getReference();
		}

		/**
		 * @inheritDoc
		 */
		public String getUrl(String rootProperty)
		{
			return getUrl();
		}

		/**
		 * @inheritDoc
		 */
		public ResourceProperties getProperties()
		{
			// if lazy, resolve
			if (((BaseResourceProperties) m_properties).isLazy())
			{
				((BaseResourcePropertiesEdit) m_properties).setLazy(false);
				m_storage.readProperties(this, m_properties);
			}

			return m_properties;
		}

		/**
		 * @inheritDoc
		 */
		public User getCreatedBy()
		{
			try
			{
				return getUser(m_createdUserId);
			}
			catch (Exception e)
			{
				return getAnonymousUser();
			}
		}

		/**
		 * @inheritDoc
		 */
		public User getModifiedBy()
		{
			try
			{
				return getUser(m_lastModifiedUserId);
			}
			catch (Exception e)
			{
				return getAnonymousUser();
			}
		}

		/**
		 * @inheritDoc
		 */
		public Time getCreatedTime()
		{
			return m_createdTime;
		}

		/**
		 * @inheritDoc
		 */
		public Time getModifiedTime()
		{
			return m_lastModifiedTime;
		}

		/**
		 * @inheritDoc
		 */
		public String getDisplayName()
		{
			String rv = null;

			// If a contextual aliasing service exists, let it have the first try.
			if (m_contextualUserDisplayService != null) {
				rv = m_contextualUserDisplayService.getUserDisplayName(this);
				if (rv != null) {
					return rv;
				}
			}

			// let the provider handle it, if we have that sort of provider, and it wants to handle this
			if ((m_provider != null) && (m_provider instanceof DisplayAdvisorUDP))
			{
				rv = ((DisplayAdvisorUDP) m_provider).getDisplayName(this);
			}

			if (rv == null)
			{
				// or do it this way
				StringBuilder buf = new StringBuilder(128);
				if (m_firstName != null) buf.append(m_firstName);
				if (m_lastName != null)
				{
					buf.append(" ");
					buf.append(m_lastName);
				}

				if (buf.length() == 0)
				{
					rv = getEid();
				}

				else
				{
					rv = buf.toString();
				}
			}

			return rv;
		}

		/**
		 * @inheritDoc
		 */
		public String getDisplayId()
		{
			String rv = null;
			
			// If a contextual aliasing service exists, let it have the first try.
			if (m_contextualUserDisplayService != null) {
				rv = m_contextualUserDisplayService.getUserDisplayId(this);
				if (rv != null) {
					return rv;
				}
			}

			// let the provider handle it, if we have that sort of provider, and it wants to handle this
			if ((m_provider != null) && (m_provider instanceof DisplayAdvisorUDP))
			{
				rv = ((DisplayAdvisorUDP) m_provider).getDisplayId(this);
			}

			// use eid if not
			if (rv == null)
			{
				rv = getEid();
			}

			return rv;
		}

		/**
		 * @inheritDoc
		 */
		public String getFirstName()
		{
			if (m_firstName == null) return "";
			return m_firstName;
		}

		/**
		 * @inheritDoc
		 */
		public String getLastName()
		{
			if (m_lastName == null) return "";
			return m_lastName;
		}

		/**
		 * @inheritDoc
		 */
		public String getSortName()
		{
			if (m_sortName == null)
			{
				// Cache this locally in the object as otherwise when sorting users we generate lots of objects.
				StringBuilder buf = new StringBuilder(128);
				if (m_lastName != null) buf.append(m_lastName);
				if (m_firstName != null)
				{
					buf.append(", ");
					buf.append(m_firstName);
				}

				m_sortName = (buf.length() == 0)?getEid():buf.toString();
			}
			return m_sortName;
		}

		/**
		 * @inheritDoc
		 */
		public String getEmail()
		{
			if (m_email == null) return "";
			return m_email;
		}

		/**
		 * @inheritDoc
		 */
		public String getType()
		{
			return m_type;
		}

		/**
		 * @inheritDoc
		 */
		public boolean checkPassword(String pw)
		{
			pw = StringUtil.trimToNull(pw);

			return m_pwdService.check(pw, m_pw);
		}

		/**
		 * @inheritDoc
		 */
		public boolean equals(Object obj)
		{
			if (!(obj instanceof User)) return false;
			// ID might not be defined if the user is a provided one and we attempting to search for it.
			return (getId() != null && getId().equals(((User)obj).getId())) ||
				(getEid() != null && getEid().equals(((User)obj).getEid()));
		}

		/**
		 * @inheritDoc
		 */
		public int hashCode()
		{
			String id = getId();
			if (id == null)
			{
				// Maintains consistency with Sakai 2.4.x behavior.
				id = "";
			}
			return id.hashCode();
		}

		/**
		 * @inheritDoc
		 */
		public int compareTo(Object obj)
		{
			if (!(obj instanceof User)) throw new ClassCastException();

			// if the object are the same, say so
			if (obj == this) return 0;

			// start the compare by comparing their sort names
			int compare = getSortName().compareTo(((User) obj).getSortName());

			// if these are the same
			if (compare == 0)
			{
				// sort based on (unique) eid
				compare = getEid().compareTo(((User) obj).getEid());
			}

			return compare;
		}

		/**
		 * Clean up.
		 */
		protected void finalize()
		{
			// catch the case where an edit was made but never resolved
			if (m_active)
			{
				cancelEdit(this);
			}
		}

		/**
		 * @inheritDoc
		 */
		public void setId(String id)
		{
			// set once only!
			if (m_id == null)
			{
				m_id = id;
			}
			else throw new UnsupportedOperationException("Tried to change user ID from " + m_id + " to " + id);
		}

		/**
		 * @inheritDoc
		 */
		public void setEid(String eid)
		{
			m_eid = eid;
			m_sortName = null;
		}

		/**
		 * @inheritDoc
		 */
		public void setFirstName(String name)
		{
		    if(!m_restrictedFirstName) {
		    	m_firstName = name;
		    	m_sortName = null;
		    }
		}

		/**
		 * @inheritDoc
		 */
		public void setLastName(String name)
		{
			if(!m_restrictedLastName) {
		    	m_lastName = name;
		    	m_sortName = null;
		    }
		}

		/**
		 * @inheritDoc
		 */
		public void setEmail(String email)
		{
			if(!m_restrictedEmail) {
				m_email = email;
			}
		}

		/**
		 * @inheritDoc
		 */
		public void setPassword(String pw)
		{

			if(!m_restrictedPassword) {

				// to clear it
				if (pw == null)
				{
					m_pw = null;
				}

				// else encode the new one
				else
				{
					// encode this password
					String encoded = m_pwdService.encrypt(pw);
					m_pw = encoded;
				}
			}
		}

		/**
		 * @inheritDoc
		 */
		public void setType(String type)
		{
			if(!m_restrictedType) {

				m_type = type;

			}
		}

		public void restrictEditFirstName() {

			m_restrictedFirstName = true;

		}

		public void restrictEditLastName() {

			m_restrictedLastName = true;

		}



		public void restrictEditEmail() {

			m_restrictedEmail = true;

		}

		public void restrictEditPassword() {

			m_restrictedPassword = true;

		}

		public void restrictEditType() {

			m_restrictedType = true;

		}

		/**
		 * Take all values from this object.
		 *
		 * @param user
		 *        The user object to take values from.
		 */
		protected void set(User user)
		{
			setAll(user);
		}

		/**
		 * Access the event code for this edit.
		 *
		 * @return The event code for this edit.
		 */
		protected String getEvent()
		{
			return m_event;
		}

		/**
		 * Set the event code for this edit.
		 *
		 * @param event
		 *        The event code for this edit.
		 */
		protected void setEvent(String event)
		{
			m_event = event;
		}

		/**
		 * @inheritDoc
		 */
		public ResourcePropertiesEdit getPropertiesEdit()
		{
			// if lazy, resolve
			if (((BaseResourceProperties) m_properties).isLazy())
			{
				((BaseResourcePropertiesEdit) m_properties).setLazy(false);
				m_storage.readProperties(this, m_properties);
			}

			return m_properties;
		}

		/**
		 * Enable editing.
		 */
		protected void activate()
		{
			m_active = true;
		}

		/**
		 * @inheritDoc
		 */
		public boolean isActiveEdit()
		{
			return m_active;
		}

		/**
		 * Close the edit object - it cannot be used after this.
		 */
		protected void closeEdit()
		{
			m_active = false;
		}

		/**
		 * Check this User object to see if it is selected by the criteria.
		 *
		 * @param criteria
		 *        The critera.
		 * @return True if the User object is selected by the criteria, false if not.
		 */
		protected boolean selectedBy(String criteria)
		{
			if (StringUtil.containsIgnoreCase(getSortName(), criteria) || StringUtil.containsIgnoreCase(getDisplayName(), criteria)
					|| StringUtil.containsIgnoreCase(getEid(), criteria) || StringUtil.containsIgnoreCase(getEmail(), criteria))
			{
				return true;
			}

			return false;
		}

		/******************************************************************************************************************************************************************************************************************************************************
		 * SessionBindingListener implementation
		 *****************************************************************************************************************************************************************************************************************************************************/

		/**
		 * @inheritDoc
		 */
		public void valueBound(SessionBindingEvent event)
		{
		}

		/**
		 * @inheritDoc
		 */
		public void valueUnbound(SessionBindingEvent event)
		{
			if (M_log.isDebugEnabled()) M_log.debug("valueUnbound()");

			// catch the case where an edit was made but never resolved
			if (m_active)
			{
				cancelEdit(this);
			}
		}
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Storage
	 *********************************************************************************************************************************************************************************************************************************************************/

	protected interface Storage
	{
		/**
		 * Open.
		 */
		public void open();

		/**
		 * Close.
		 */
		public void close();

		/**
		 * Check if a user by this id exists.
		 *
		 * @param id
		 *        The user id.
		 * @return true if a user by this id exists, false if not.
		 */
		public boolean check(String id);

		/**
		 * Get the user with this id, or null if not found.
		 *
		 * @param id
		 *        The user id.
		 * @return The user with this id, or null if not found.
		 */
		public UserEdit getById(String id);

		/**
		 * Get the users with this email, or return empty if none found.
		 *
		 * @param id
		 *        The user email.
		 * @return The Collection (User) of users with this email, or an empty collection if none found.
		 */
		public Collection findUsersByEmail(String email);

		/**
		 * Get all users.
		 *
		 * @return The List (UserEdit) of all users.
		 */
		public List getAll();

		/**
		 * Get all the users in record range.
		 *
		 * @param first
		 *        The first record position to return.
		 * @param last
		 *        The last record position to return.
		 * @return The List (BaseUserEdit) of all users.
		 */
		public List getAll(int first, int last);

		/**
		 * Count all the users.
		 *
		 * @return The count of all users.
		 */
		public int count();

		/**
		 * Search for users with id or email, first or last name matching criteria, in range.
		 *
		 * @param criteria
		 *        The search criteria.
		 * @param first
		 *        The first record position to return.
		 * @param last
		 *        The last record position to return.
		 * @return The List (BaseUserEdit) of all alias.
		 */
		public List search(String criteria, int first, int last);

		/**
		 * Count all the users with id or email, first or last name matching criteria.
		 *
		 * @param criteria
		 *        The search criteria.
		 * @return The count of all aliases with id or target matching criteria.
		 */
		public int countSearch(String criteria);

		/**
		 * Add a new user with this id and eid.
		 *
		 * @param id
		 *        The user id.
		 * @param eid
		 *        The user eid.
		 * @return The locked User object with this id and eid, or null if the id is in use.
		 */
		public UserEdit put(String id, String eid);

		/**
		 * Get a lock on the user with this id, or null if a lock cannot be gotten.
		 *
		 * @param id
		 *        The user id.
		 * @return The locked User with this id, or null if this records cannot be locked.
		 */
		public UserEdit edit(String id);

		/**
		 * Commit the changes and release the lock.
		 *
		 * @param user
		 *        The user to commit.
		 * @return true if successful, false if not (eid may not be unique).
		 */
		public boolean commit(UserEdit user);

		/**
		 * Cancel the changes and release the lock.
		 *
		 * @param user
		 *        The user to commit.
		 */
		public void cancel(UserEdit user);

		/**
		 * Remove this user.
		 *
		 * @param user
		 *        The user to remove.
		 */
		public void remove(UserEdit user);

		/**
		 * Read properties from storage into the edit's properties.
		 *
		 * @param edit
		 *        The user to read properties for.
		 */
		public void readProperties(UserEdit edit, ResourcePropertiesEdit props);

		/**
		 * Create a mapping between the id and eid.
		 *
		 * @param id
		 *        The user id.
		 * @param eid
		 *        The user eid.
		 * @return true if successful, false if not (id or eid might be in use).
		 */
		public boolean putMap(String id, String eid);

		/**
		 * Check the id -> eid mapping: lookup this id and return the eid if found
		 *
		 * @param id
		 *        The user id to lookup.
		 * @return The eid mapped to this id, or null if none.
		 */
		public String checkMapForEid(String id);

		/**
		 * Check the id -> eid mapping: lookup this eid and return the id if found
		 *
		 * @param eid
		 *        The user eid to lookup.
		 * @return The id mapped to this eid, or null if none.
		 */
		public String checkMapForId(String eid);
		
		/**
		 * Since optimizing this call requires access to SQL result sets and
		 * internally-maintained caches, all the real work is performed by
		 * the storage class. 
		 * 
		 * @param ids
		 * @return any user records with matching IDs
		 */
		public List<User> getUsersByIds(Collection<String> ids);
		
		/**
		 * Since optimizing this call requires access to SQL result sets and
		 * internally-maintained caches, all the real work is performed by
		 * the storage class. 
		 * 
		 * @param eids
		 * @return any user records with matching EIDs
		 */
		public List<User> getUsersByEids(Collection<String> eids);
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * StorageUser implementation (no container)
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * @inheritDoc
	 */
	public Entity newContainer(String ref)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Entity newContainer(Element element)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Entity newContainer(Entity other)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Entity newResource(Entity container, String id, Object[] others)
	{
		return new BaseUserEdit(id);
	}

	/**
	 * @inheritDoc
	 */
	public Entity newResource(Entity container, Element element)
	{
		return new BaseUserEdit(element);
	}

	/**
	 * @inheritDoc
	 */
	public Entity newResource(Entity container, Entity other)
	{
		return new BaseUserEdit((User) other);
	}

	/**
	 * @inheritDoc
	 */
	public Edit newContainerEdit(String ref)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Edit newContainerEdit(Element element)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Edit newContainerEdit(Entity other)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Edit newResourceEdit(Entity container, String id, Object[] others)
	{
		BaseUserEdit e = new BaseUserEdit(id);
		e.activate();
		return e;
	}

	/**
	 * @inheritDoc
	 */
	public Edit newResourceEdit(Entity container, Element element)
	{
		BaseUserEdit e = new BaseUserEdit(element);
		e.activate();
		return e;
	}

	/**
	 * @inheritDoc
	 */
	public Edit newResourceEdit(Entity container, Entity other)
	{
		BaseUserEdit e = new BaseUserEdit((User) other);
		e.activate();
		return e;
	}

	/**
	 * @inheritDoc
	 */
	public Object[] storageFields(Entity r)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public boolean isDraft(Entity r)
	{
		return false;
	}

	/**
	 * @inheritDoc
	 */
	public String getOwnerId(Entity r)
	{
		return null;
	}

	/**
	 * @inheritDoc
	 */
	public Time getDate(Entity r)
	{
		return null;
	}

}
