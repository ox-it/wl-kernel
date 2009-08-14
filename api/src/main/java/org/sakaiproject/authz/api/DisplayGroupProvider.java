package org.sakaiproject.authz.api;

/**
 * Allow for nice displaynames of groups provided to a site.
 * @author buckett
 *
 */
public interface DisplayGroupProvider {

	/**
	 * Get the nice display name for the provided group.
	 * @param groupId The group ID.
	 * @return A name to display to the user for the supplied group.
	 */
	public String getGroupName(String groupId);
}
