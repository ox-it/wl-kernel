package org.sakaiproject.content.impl.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.*;
import org.sakaiproject.content.api.*;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.exception.*;
import org.sakaiproject.test.SakaiKernelTestBase;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class RoleViewTest extends SakaiKernelTestBase {

    private static final Log log = LogFactory.getLog(RoleViewTest.class);

    protected static final String PHOTOS_COLLECTION = "/private/images/photos/";
    protected static final String TEST_ROLE         = "com.roles.test";

    protected ContentHostingService _chs;
    protected AuthzGroupService _ags;

    public static Test suite()
    {
        TestSetup setup = new TestSetup(new TestSuite(RoleViewTest.class))
        {
            protected void setUp() throws Exception
            {
                log.debug("starting oneTimeSetup");
                oneTimeSetup(null);
                log.debug("finished oneTimeSetup");
            }
            protected void tearDown() throws Exception
            {
                log.debug("starting tearDown");
                oneTimeTearDown();
                log.debug("finished tearDown");
            }
        };
        return setup;
    }

    public void setUp() throws IdUsedException, IdInvalidException, InconsistentException, PermissionException {
        _chs = org.sakaiproject.content.cover.ContentHostingService.getInstance();
        _ags = org.sakaiproject.authz.cover.AuthzGroupService.getInstance();

        SessionManager sm = org.sakaiproject.tool.cover.SessionManager.getInstance();
        Session session = sm.getCurrentSession();
        session.setUserEid("admin");
        session.setUserId("admin");
        ContentCollectionEdit collectionEdit = _chs.addCollection(PHOTOS_COLLECTION);
        _chs.commitCollection(collectionEdit);
    }

    public void tearDown() throws IdUnusedException, PermissionException, InUseException, TypeException, ServerOverloadException, AuthzPermissionException {
        _chs.removeCollection(PHOTOS_COLLECTION);
        _ags.removeAuthzGroup(_chs.getReference(PHOTOS_COLLECTION));
    }

    public void testSetPubView() {
        _chs.setPubView(PHOTOS_COLLECTION, true);
        assertTrue(hasRealmAndRole(PHOTOS_COLLECTION, AuthzGroupService.ANON_ROLE));
    }

    public void testSetRoleView() throws AuthzPermissionException {
        _chs.setRoleView(PHOTOS_COLLECTION, TEST_ROLE, true);
        assertTrue(hasRealmAndRole(PHOTOS_COLLECTION, TEST_ROLE));
    }

    private boolean hasRealmAndRole(String contentId, String roleId) {
        AuthzGroup realm = null;
        try {
            realm = _ags.getAuthzGroup(_chs.getReference(contentId));
        } catch (GroupNotDefinedException e) {
            fail("Group is not defined for content " + e);
        }

        Role role = realm.getRole(roleId);
        if (role == null) {
            fail("Role is not defined for the content realm " + realm.getId());
        }

        if (!role.isAllowed(ContentHostingService.AUTH_RESOURCE_READ)) {
            fail("Read access is not defined for the role");
        }
        return true;
    }

    public void testPubView() {
        assertFalse(_chs.isPubView(PHOTOS_COLLECTION));
        assertTrue(_chs.getRoleViews(PHOTOS_COLLECTION).isEmpty());

        _chs.setPubView(PHOTOS_COLLECTION, true);
        assertTrue(_chs.getRoleViews(PHOTOS_COLLECTION).contains(AuthzGroupService.ANON_ROLE));
        assertTrue(_chs.isPubView(PHOTOS_COLLECTION));

        _chs.setPubView(PHOTOS_COLLECTION, false);
        assertTrue(_chs.getRoleViews(PHOTOS_COLLECTION).isEmpty());
        assertFalse(_chs.isPubView(PHOTOS_COLLECTION));
    }

    public void testRoleView() throws AuthzPermissionException {
        assertFalse(_chs.isRoleView(PHOTOS_COLLECTION, TEST_ROLE));
        assertTrue(_chs.getRoleViews(PHOTOS_COLLECTION).isEmpty());

        _chs.setRoleView(PHOTOS_COLLECTION, TEST_ROLE, true);
        assertTrue(_chs.getRoleViews(PHOTOS_COLLECTION).contains(TEST_ROLE));
        assertTrue(_chs.isRoleView(PHOTOS_COLLECTION, TEST_ROLE));

        _chs.setRoleView(PHOTOS_COLLECTION, TEST_ROLE, false);
        assertTrue(_chs.getRoleViews(PHOTOS_COLLECTION).isEmpty());
        assertFalse(_chs.isRoleView(PHOTOS_COLLECTION, TEST_ROLE));
    }

}

