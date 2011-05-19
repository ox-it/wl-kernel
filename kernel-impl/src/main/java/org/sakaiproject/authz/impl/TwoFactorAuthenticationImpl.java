package org.sakaiproject.authz.impl;

import org.sakaiproject.authz.api.TwoFactorAuthentication;
import org.sakaiproject.component.api.ServerConfigurationService;

public class TwoFactorAuthenticationImpl implements TwoFactorAuthentication {

	private boolean enabled;
	
	private ServerConfigurationService serverConfigurationService;
	
	public void setServerConfigurationService(
			ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	public void init() {
		enabled = serverConfigurationService.getBoolean("twofactor.enable", false);
	}
	
	public boolean hasTwoFactor() {
		// TODO Auto-generated method stub
		return false;
	}

	public void markTwoFactor() {
		// TODO Auto-generated method stub

	}

	public boolean isTwoFactorRequired(String ref) {
		if (!enabled) {
			return false;
		}
		//TODO Actual calculations
		return false;
	}

}
