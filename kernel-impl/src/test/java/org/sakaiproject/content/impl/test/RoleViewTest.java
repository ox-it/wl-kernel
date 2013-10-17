package org.sakaiproject.content.impl.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.content.api.*;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.exception.*;
import org.sakaiproject.memory.api.MemoryPermissionException;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.test.SakaiKernelTestBase;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class RoleViewTest extends SakaiKernelTestBase {

    private static final Log log = LogFactory.getLog(RoleViewTest.class);

    protected static final String IMAGES_COLLECTION = "/images";
    protected static final String PHOTOS_COLLECTION = "/images/photos";
    protected static final String TEST_ROLE         = "com.roles.test";

    protected ContentHostingService _chs;
    protected MemoryService _ms;
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

    @Before
    public void setUp() throws IdUsedException, IdInvalidException, InconsistentException, PermissionException {
        _ms = org.sakaiproject.memory.cover.MemoryServiceLocator.getInstance();
        _chs = org.sakaiproject.content.cover.ContentHostingService.getInstance();
        _ags = org.sakaiproject.authz.cover.AuthzGroupService.getInstance();

        SessionManager sm = org.sakaiproject.tool.cover.SessionManager.getInstance();
        Session session = sm.getCurrentSession();
        session.setUserEid("admin");
        session.setUserId("admin");
        ContentCollectionEdit collectionEdit = _chs.addCollection(PHOTOS_COLLECTION);
        _chs.commitCollection(collectionEdit);
    }

    @After
    public void tearDown() throws IdUnusedException, PermissionException, InUseException, TypeException, ServerOverloadException {
        _chs.removeCollection(PHOTOS_COLLECTION);
    }

    @Ignore
    public void testSetPubView() {
        _chs.setPubView(PHOTOS_COLLECTION, true);
        assertTrue(hasRealmAndRole(PHOTOS_COLLECTION, AuthzGroupService.ANON_ROLE));
    }

    @Ignore
    public void testSetRoleView() {
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

    @Ignore
    public void testPubView() {
        assertFalse(_chs.isPubView(PHOTOS_COLLECTION));

        _chs.setPubView(PHOTOS_COLLECTION, true);
        assertTrue(_chs.isPubView(PHOTOS_COLLECTION));

        _chs.setPubView(PHOTOS_COLLECTION, false);
        assertFalse(_chs.isPubView(PHOTOS_COLLECTION));
    }

    @Ignore
    public void testRoleView() {
        assertFalse(_chs.isRoleView(PHOTOS_COLLECTION, TEST_ROLE));

        _chs.setRoleView(PHOTOS_COLLECTION, TEST_ROLE, true);
        assertTrue(_chs.isRoleView(PHOTOS_COLLECTION, TEST_ROLE));

        _chs.setRoleView(PHOTOS_COLLECTION, TEST_ROLE, false);
        assertFalse(_chs.isRoleView(PHOTOS_COLLECTION, TEST_ROLE));
    }

}

