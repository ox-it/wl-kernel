package org.sakaiproject.authz.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.TwoFactorAuthentication;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class TwoFactorAuthenticationImpl implements TwoFactorAuthentication {

	private static final Log log = LogFactory.getLog(TwoFactorAuthenticationImpl.class);
	
	private boolean enabled;
	
	private ServerConfigurationService serverConfigurationService;
	
	private SessionManager sessionManager;
	
	private EntityManager entityManager;
	
	private SiteService siteService;
	
	private String siteType;
	
	public void setServerConfigurationService(
			ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}
	
	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}
	
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void init() {
		enabled = serverConfigurationService.getBoolean("twofactor.enable", false);
		siteType = serverConfigurationService.getString("twofactor.site.type", "secure");
	}
	
	public boolean hasTwoFactor() {
		Session session = sessionManager.getCurrentSession();
		Long timeout = (Long)session.getAttribute(SessionManager.TWOFACTORAUTHENTICATION);
		if (null != timeout) {
			if (System.currentTimeMillis() < timeout) {
				return true;
			} else {
				return false;
			}
		}
		return false;
	}

	public void markTwoFactor() {
		Session session = sessionManager.getCurrentSession();
		session.setAttribute(SessionManager.TWOFACTORAUTHENTICATION, 
				System.currentTimeMillis() + SessionManager.EXPIREMILIS);

	}

	public boolean isTwoFactorRequired(String ref) {
		// This checks if two factor authentication is required based on the type of the site.
		// For references that don't have a valid context we won't be able to work out the type
		// of the site and so won't know if we should be requiring two factor access.
		if (!enabled) {
			return false;
		}
		if (ref != null) {
			Reference reference = entityManager.newReference(ref);
			if (SiteService.APPLICATION_ID.equals(reference.getType())) {
				Object entity = reference.getEntity();
				if (entity instanceof Site) {
					Site site = (Site)entity;
					if (siteType.equals(site.getType())) {
						return true;
					}
				}
			} else {
				// If this is a reference for something other than a site then check for the 
				// context and use the site.
				String siteId = reference.getContext();
				try {
					Site site = siteService.getSite(siteId);
					if (siteType.equals(site.getType())) {
						return true;
					}
				} catch (IdUnusedException iue) {
					if (log.isInfoEnabled()) {
						log.info("Failed to find site: "+siteId+ " for reference: "+ ref);
					}
				}
			}
		}
		return false;
	}

}
