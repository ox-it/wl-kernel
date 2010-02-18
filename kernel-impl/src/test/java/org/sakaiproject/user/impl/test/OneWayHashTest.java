package org.sakaiproject.user.impl.test;

import org.sakaiproject.user.impl.OneWayHash;

import junit.framework.TestCase;

public class OneWayHashTest extends TestCase {

	public void testEncode() {
		assertEquals("ISMvKXpXpadDiUoOSoAfww==", OneWayHash.encode("admin", false));
		assertEquals("ISMvKXpXpadDiUoOSoAf", OneWayHash.encode("admin", true));
		assertEquals("1B2M2Y8AsgTpgAmY7PhCfg==", OneWayHash.encode("", false));
	}

}