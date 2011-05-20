package org.sakaiproject.authz.impl;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.sakaiproject.authz.api.TwoFactorAuthentication;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.id.cover.IdManager;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.test.SakaiKernelTestBase;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;

public class TwoFactorAuthenticationIntTest extends SakaiKernelTestBase {

	public static Test suite() {
		TestSetup setup = new TestSetup(new TestSuite(TwoFactorAuthenticationIntTest.class)) {
			protected void setUp() throws Exception {
				oneTimeSetup("two_factor");
			}
			protected void tearDown() throws Exception {
				oneTimeTearDown();
			}
		};
		return setup;
	}

	/**
	 * Fuller integration test of two factor authentication stopping a user from accessing a site.
	 */
	public void testTwoFactorRequired() throws Exception {
		
		SiteService siteService = (SiteService)ComponentManager.get(SiteService.class);
		
		SessionManager sessionManager = (SessionManager)ComponentManager.get(SessionManager.class);
		
		UserDirectoryService userDirectoryService = (UserDirectoryService)ComponentManager.get(UserDirectoryService.class);
		
		TwoFactorAuthentication twoFactorAuthentication = (TwoFactorAuthentication)ComponentManager.get(TwoFactorAuthentication.class);
		

		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserEid("admin");
		sakaiSession.setUserId("admin");
		
		UserEdit user = userDirectoryService.addUser("other", "other");
		user.setFirstName("First");
		user.setLastName("Last");
		userDirectoryService.commitEdit(user);
		
		String id = IdManager.createUuid();
		Site secureSite = siteService.addSite(id, "project");
		secureSite.setTitle("A Secure Site");
		secureSite.setPublished(true);
		secureSite.addMember("other", "access", true, false);
		siteService.save(secureSite);
		
		assertFalse(twoFactorAuthentication.isTwoFactorRequired(secureSite.getReference()));
	
		try {
			Site site = siteService.getSiteVisit(id);
		} catch (Exception e) {
			fail("Admin should be able to get to the site.");
		}
		
		sakaiSession.setUserEid("other");
		sakaiSession.setUserId("other");
		try {
			Site site = siteService.getSiteVisit(id);
		} catch (Exception e) {
			fail("Other user should be able to access the site as it's not yet secure");
		}

		sakaiSession.setUserEid("admin");
		sakaiSession.setUserId("admin");
		
		secureSite.setType("secure");
		siteService.save(secureSite);
		
		assertTrue(twoFactorAuthentication.isTwoFactorRequired(secureSite.getReference()));
		
		sakaiSession.setUserEid("other");
		sakaiSession.setUserId("other");
		try {
			Site site = siteService.getSiteVisit(id);
			fail("As we don't have two factor auth yet, this should fail.");
		} catch (Exception e) {
		}
		
	}
	
}
