package org.sakaiproject.authz.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.LogFactoryImpl;
import org.sakaiproject.authz.api.DevolvedAdminDao;
import org.sakaiproject.authz.api.DevolvedSakaiSecurity;
import org.sakaiproject.authz.cover.FunctionManager;
import org.sakaiproject.authz.hbm.DevolvedAdmin;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.user.api.User;

/**
 * Oxford version of Sakai security.
 * 
 * @author buckett
 * 
 */
public abstract class DevolvedSakaiSecurityImpl extends SakaiSecurity implements
		DevolvedSakaiSecurity {
	
	final public static String ADMIN_REALM_CHANGE = "site.admin.change";
	
	private static Log log = LogFactoryImpl
			.getLog(DevolvedSakaiSecurityImpl.class);
	
	private String adminSiteType;
	
	private Cache adminCache;

	private Observer siteDeleteObserver;
	
	protected abstract EventTrackingService eventTrackingService();
		
	public void init() {
		super.init();
		FunctionManager.registerFunction(ADMIN_REALM_PERMISSION);
		FunctionManager.registerFunction(ADMIN_REALM_PERMISSION_USE);
		log.info("Admin site type set to: "+ adminSiteType);
		
		adminCache = memoryService().newCache(DevolvedSakaiSecurityImpl.class.getName(), SiteService.REFERENCE_ROOT);
		
		siteDeleteObserver = new Observer() {
		
			public void update(Observable o, Object arg) {
				if (arg instanceof Event) {
					Event event = (Event)arg;
					if (SiteService.SECURE_REMOVE_SITE.equals(event.getEvent())) {
						dao().delete(event.getResource());
					}
				}
		
			}
		};
		eventTrackingService().addLocalObserver(siteDeleteObserver);
	}
	
	public void destroy() {
		super.destroy();
		eventTrackingService().deleteObserver(siteDeleteObserver);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.authz.impl.DevolvedSakaiSecurity#getAdminRealm(java.lang.String)
	 */
	public String getAdminRealm(String entityRef) {
		String adminRealm = (String) adminCache.get(entityRef);
		// We want to cache nulls to need to look and see of the cache has an entry first.
		// If we check to see if the entry is in the cache first then our hit/miss stats our wrong.
		if (adminRealm == null && !adminCache.containsKey(entityRef) ) {
			DevolvedAdmin admin = dao().findByRealm(entityRef);
			adminRealm = (admin != null) ? admin.getAdminRealm() : null;
			adminCache.put(entityRef, adminRealm);
		}
		return adminRealm;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.authz.impl.DevolvedSakaiSecurity#setAdminRealm(java.lang.String,
	 *      java.lang.String)
	 */
	public void setAdminRealm(String entityRef, String adminRealm)
			throws PermissionException {
		getSiteReference(entityRef);
		User user = userDirectoryService().getCurrentUser();
		if (!canSetAdminRealm(entityRef)) {
			throw new PermissionException(user.getId(), SiteService.SECURE_UPDATE_SITE, entityRef);
		}
		if (!canUseAdminRealm(adminRealm)) {
			throw new PermissionException(user.getId(), ADMIN_REALM_PERMISSION_USE, adminRealm);
		}
		DevolvedAdmin admin = dao().findByRealm(entityRef);
		if (admin == null) {
			admin = new DevolvedAdmin();
			admin.setRealm(entityRef);
		}
		admin.setAdminRealm(adminRealm);
		dao().save(admin);
		eventTrackingService().post(eventTrackingService().newEvent(ADMIN_REALM_CHANGE, entityRef, true));
	}

	public boolean canSetAdminRealm(String entityRef) {
		Reference ref = entityManager().newReference(entityRef);
		if (SiteService.APPLICATION_ID.equals(ref.getType())
				&& SiteService.SITE_SUBTYPE.equals(ref.getSubType())) {
			return unlock(SiteService.SECURE_UPDATE_SITE, entityRef);
		}
		return false;
	}
	
	public boolean canUseAdminRealm(String adminRealm) {
		return unlock(ADMIN_REALM_PERMISSION_USE, adminRealm);
	}
	
	public boolean canRemoveAdminRealm(String adminRealm) {
		return isSuperUser();
	}

	public List<Entity> getAvailableAdminRealms(String entityRef) {
		if (entityRef != null) {
			Reference ref = getSiteReference(entityRef);
		}
		// Must have some sort of filtering as we can't iterate over all the sites.
		List<Site> sites = siteService().getSites(SelectionType.ANY, adminSiteType, null, null, null, null);
		List <Entity> entities = new ArrayList<Entity>();
		for (Site site : sites) {
			if (unlock(ADMIN_REALM_PERMISSION_USE, site.getReference()) && !site.getReference().equals(entityRef)) {
				entities.add(site);
			}
		}
		return entities;
	}
	
	public List<Entity> findUsesOfAdmin(String adminRealm) {
		List<DevolvedAdmin> devolvedAdmins = dao().findByAdminRealm(adminRealm);
		List <Entity> sites = new ArrayList<Entity>(devolvedAdmins.size());
		for (DevolvedAdmin devolvedAdmin: devolvedAdmins)
		{
			Entity entity = getSiteReference(devolvedAdmin.getRealm()).getEntity();
			if (entity != null) {
				sites.add(entity);
			}
		}
		return sites;
	}
	
	public void removeAdminRealm(String adminRealm) throws PermissionException {
		Reference ref = getSiteReference(adminRealm);
		if (canRemoveAdminRealm(adminRealm)) {
			dao().delete(adminRealm);
		} else {
			throw new PermissionException(null,null,null);
		}
	}
	
	/**
	 * Get a reference for a site entity.
	 * @throws IllegalArgumentException If the entity supplied isn't a site one.
	 * @param entityRef 
	 * @return The reference.
	 */
	private Reference getSiteReference(String entityRef) {
		Reference ref = entityManager().newReference(entityRef);
		if (SiteService.APPLICATION_ID.equals(ref.getType())
				&& SiteService.SITE_SUBTYPE.equals(ref.getSubType())) {
			return ref;
		} else {
			throw new IllegalArgumentException(
					"Only site entities are supported at the moment. Entity: "
							+ entityRef);
		}
		
	}


	protected boolean checkAuthzGroups(String userId, String function, String entityRef, Collection azgs)
	{
		// check the cache
		String command = "unlock@" + userId + "@" + function + "@" + entityRef;
		if (m_callCache != null)
		{
			final Boolean value = (Boolean) m_callCache.get(command);
			if(value != null) return value.booleanValue();
		}

		// get this entity's AuthzGroups if needed
		if (azgs == null)
		{
			// make a reference for the entity
			Reference ref = entityManager().newReference(entityRef);

			azgs = ref.getAuthzGroups(userId);
		}

		Collection<String> expandedAzgs = new HashSet<String>();
		for (String ref: (Collection<String>)azgs) {
			// If we're in roleswap don't use adminsites as we just want to be a role in the current site.
			if (ref.startsWith(SiteService.REFERENCE_ROOT) && getUserEffectiveRole(ref) != null) {
				expandedAzgs = azgs;
				break;
			}
			String adminRealm = getAdminRealm(ref);
			if (adminRealm != null) {
				if (log.isDebugEnabled()) {
					log.debug("Adding admin realm: " + adminRealm + " for: " + ref);
				}
				expandedAzgs.add(adminRealm);
			}
			expandedAzgs.add(ref);
		}

		boolean rv = authzGroupService().isAllowed(userId, function, expandedAzgs);

		// cache
		if (m_callCache != null) m_callCache.put(command, Boolean.valueOf(rv), m_cacheMinutes * 60, entityRef, expandedAzgs);

		return rv;
	}

	protected abstract SiteService siteService();

	protected abstract DevolvedAdminDao dao();

	protected abstract MemoryService memory();

	public String getAdminSiteType() {
		return adminSiteType;
	}

	public void setAdminSiteType(String adminSiteType) {
		this.adminSiteType = adminSiteType;
	}
}