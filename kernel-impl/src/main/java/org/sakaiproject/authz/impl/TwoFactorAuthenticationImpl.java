package org.sakaiproject.authz.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.TwoFactorAuthentication;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class TwoFactorAuthenticationImpl implements TwoFactorAuthentication {

	private static final Log log = LogFactory.getLog(TwoFactorAuthenticationImpl.class);
	
	private boolean enabled;
	
	private ServerConfigurationService serverConfigurationService;
	
	private SessionManager sessionManager;
	
	private EntityManager entityManager;
	
	private SiteService siteService;
	
	private ThreadLocalManager threadLocalManager;
	
	private String siteType;
	
	private long timeout;
	
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
	
	public void setThreadLocalManager(ThreadLocalManager threadLocalManager) {
		this.threadLocalManager = threadLocalManager;
	}

	public void init() {
		enabled = serverConfigurationService.getBoolean("twofactor.enable", false);
		siteType = serverConfigurationService.getString("twofactor.site.type", "secure");
		timeout = serverConfigurationService.getInt("twofactor.timeout", 900000);
	}
	
	public boolean hasTwoFactor() {
		Session session = sessionManager.getCurrentSession();
		Long timeout = (Long)session.getAttribute(SessionManager.TWOFACTORAUTHENTICATION);
		if (null != timeout) {
			log.debug("hasTwoFactor ["+System.currentTimeMillis()+":"+timeout+"]");
			if (System.currentTimeMillis() < timeout) {
				log.debug("hasTwoFactor [true]");
				return true;
			} else {
				log.debug("hasTwoFactor timeout [false]");
				return false;
			}
		}
		log.debug("hasTwoFactor [false]");
		return false;
	}

	public void markTwoFactor() {
		Session session = sessionManager.getCurrentSession();
		long expire = System.currentTimeMillis() + timeout;
		session.setAttribute(SessionManager.TWOFACTORAUTHENTICATION, expire);
		log.debug("markTwoFactor ["+expire+"]");

	}

	public boolean isTwoFactorRequired(String ref) {
		if (log.isErrorEnabled()) {
			log.debug("isTwoFactorRequired ["+ref+"]");
		}
		// This checks if two factor authentication is required based on the type of the site.
		// For references that don't have a valid context we won't be able to work out the type
		// of the site and so won't know if we should be requiring two factor access.
		if (!enabled) {
			return false;
		}
		if (ref != null) {
			// Prevent stack overflow, when an unlock happens from inside here:
			List<String> checked = (List<String>) threadLocalManager.get(TwoFactorAuthenticationImpl.class.getName());
			if (checked == null) {
				checked = new ArrayList<String>();
				threadLocalManager.set(TwoFactorAuthenticationImpl.class.getName(), checked);
			}
			if (checked.contains(ref)) {
				return false; // This allows the required entity to load.
			}
			try {
				checked.add(ref);
				Reference reference = entityManager.newReference(ref);
				if (SiteService.APPLICATION_ID.equals(reference.getType())) {
					Object entity = reference.getEntity();
					if (entity instanceof Site) {
						Site site = (Site)entity;
						if (siteType.equals(site.getType())) {
							log.debug("isTwoFactorRequired [true]");
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
							log.debug("isTwoFactorRequired [true]");
							return true;
						}
					} catch (IdUnusedException iue) {
						if (log.isInfoEnabled()) {
							log.info("Failed to find site: "+siteId+ " for reference: "+ ref);
						}
					}
				}
			} finally {
				if(!checked.isEmpty()) {
					checked.remove(checked.size()-1);
				}
			}
		}
		log.debug("isTwoFactorRequired [false]");
		return false;
	}

}
