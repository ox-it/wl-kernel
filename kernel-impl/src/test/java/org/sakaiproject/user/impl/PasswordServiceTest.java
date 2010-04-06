package org.sakaiproject.user.impl;

import org.sakaiproject.user.impl.PasswordService;

import junit.framework.TestCase;

/**
 * Test the new password code.
 * Needs to be in this package as it uses package visibilty methods.
 * @author buckett
 *
 */
public class PasswordServiceTest extends TestCase {
	
	PasswordService pwdService;
	
	public void setUp() throws Exception {
		super.setUp();
		pwdService = new PasswordService();
	}
	
	public void testCheck() {
		
		assertFalse(pwdService.check("admin", "doesn't match"));
		
		// Test of old password.
		assertTrue(pwdService.check("admin", "ISMvKXpXpadDiUoOSoAf"));
		assertTrue(pwdService.check("admin", "ISMvKXpXpadDiUoOSoAfww=="));
		
		// Test of new encryption.
		assertTrue(pwdService.check("admin", pwdService.encrypt("admin")));
		assertFalse(pwdService.check("admin", pwdService.encrypt("doesn't Match")));
		
		// Test of migrated passwords
		assertTrue(pwdService.check("admin", PasswordService.MD5_SALT_SHA256 + pwdService.encrypt("ISMvKXpXpadDiUoOSoAfww==")));
		assertTrue(pwdService.check("admin", PasswordService.MD5TRUNC_SALT_SHA256 + pwdService.encrypt("ISMvKXpXpadDiUoOSoAf")));
		assertFalse(pwdService.check("admin", PasswordService.MD5_SALT_SHA256 + pwdService.encrypt("Doesn't match.")));
		assertFalse(pwdService.check("admin", PasswordService.MD5TRUNC_SALT_SHA256 + pwdService.encrypt("Not the same")));
		
		// Test of unsalted passwords (we don't create these).
		assertTrue(pwdService.check("secret", pwdService.hash("secret", "SHA-256")));
		assertFalse(pwdService.check("secret", pwdService.hash("different Secret", "SHA-256")));
	}
	
	public void testCheckCharacterRange() {
		// Build the string or strange characters.
		StringBuilder password = new StringBuilder(10000);
		for (char ch = 0; ch < 10000; ch++) {
			password.append(ch);
		}
		String encrypted = pwdService.encrypt(password.toString());
		assertTrue(pwdService.check(password.toString(), encrypted));
	}
	
	public void testEncrypt() {
		// Check salting is working.
		assertNotSame(pwdService.encrypt("admin"), pwdService.encrypt("admin"));
	}
	
}
