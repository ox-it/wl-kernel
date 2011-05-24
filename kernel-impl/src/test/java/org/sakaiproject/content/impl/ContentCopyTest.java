package org.sakaiproject.content.impl;

import junit.framework.TestCase;

public class ContentCopyTest extends TestCase {

	private ContentCopy contentCopy;
	private CopyContext basicCtx;

	public void setUp() throws Exception {
		super.setUp();
		contentCopy = new ContentCopy();
		basicCtx = new CopyContext("old", "new", true);
	}
	
	public void testConvertContent() {
		unaltered("");
		unaltered("sometext");
		unaltered("href=boo");
		unaltered("<a href='test.url");
		unaltered("<a href='value'>");
		unaltered("<a href='url'>Anchor</a><img src='other'>");
		
		//updated("<a href='/new/index.html'>", "<a href='/old/index.html'>");
		
	}
	
	private void updated(String expected, String source) {
		assertEquals(expected, contentCopy.convertContent(basicCtx, source, null));
	}

	private void unaltered(String expected) {
		updated(expected, expected);
	}

}
