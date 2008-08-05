/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright 2006 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
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

package org.sakaiproject.authz.api;

import java.util.List;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.user.api.User;

/**
 * <p>
 * SecurityService is the interface for Sakai security services.
 * </p>
 */
public interface SecurityService
{
	/** This string can be used to find the service in the service manager. */
	public static final String SERVICE_NAME = SecurityService.class.getName();

	/**
	 * Can the current session user unlock the lock for use with this resource?
	 * 
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource reference string.
	 * @return true, if the user can unlock the lock, false otherwise.
	 */
	boolean unlock(Entity.Permission lock, String reference);

	/**
	 * Can the specificed user unlock the lock for use with this resource?
	 * 
	 * @param user
	 *        The user.
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource reference string.
	 * @return true, if the user can unlock the lock, false otherwise.
	 */
	boolean unlock(User user, Entity.Permission lock, String reference);

	/**
	 * Can the specificed user id unlock the lock for use with this resource?
	 * 
	 * @param userId
	 *        The user id.
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource reference string.
	 * @return true, if the user can unlock the lock, false otherwise.
	 */
	boolean unlock(String userId, Entity.Permission lock, String reference);

	/**
	 * Can the specificed user id unlock the lock for use with this resource (using these authzGroups for the check)?
	 * 
	 * @param userId
	 *        The user id.
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource reference string.
	 * @param authzGroupIds
	 *        The set of authz group ids to use for the check (the reference is not consulted).
	 * @return true, if the user can unlock the lock, false otherwise.
	 */
	boolean unlock(String userId, Entity.Permission lock, String reference, List<String> authzGroupIds);

	/**
	 * Access the List of Users who can unlock the lock for use with this resource.
	 * 
	 * @param lock
	 *        The lock id string.
	 * @param reference
	 *        The resource reference string.
	 * @return A List (User) of the users can unlock the lock (may be empty).
	 */
	List<User> unlockUsers(String lock, String reference);

	/**
	 * Is this a super special super (admin) user?
	 * 
	 * @return true, if the user is a cut above the rest, false if a mere mortal.
	 */
	boolean isSuperUser();

	/**
	 * Is this user a super special super (admin) user?
	 * 
	 * @param userId
	 *        The user to test.
	 * @return true, if this user is a cut above the rest, false if a mere mortal.
	 */
	boolean isSuperUser(String userId);

	/**
	 * Establish a new SecurityAdvisor for this thread, at the top of the stack (it gets first dibs on the answer).
	 * 
	 * @param advisor
	 *        The advisor to establish
	 */
	void pushAdvisor(SecurityAdvisor advisor);

	/**
	 * Remove one SecurityAdvisor from the stack for this thread, if any exist.
	 * 
	 * @return advisor The advisor popped of, or null if the stack is empty.
	 */
	SecurityAdvisor popAdvisor();

	/**
	 * Check if there are any security advisors stacked for this thread.
	 * 
	 * @return true if some advisors are defined, false if not.
	 */
	boolean hasAdvisors();

	/**
	 * Remove any SecurityAdvisors from this thread.
	 */
	void clearAdvisors();
}
