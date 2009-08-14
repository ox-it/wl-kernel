package org.sakaiproject.user.api;

/**
 * Interface a provider should implement if the authentication ID doesn't map to the EID.
 * @author buckett
 *
 */
public interface AuthenticationIdUDP {

	public boolean getUserbyAid(String aid, UserEdit user);

}
