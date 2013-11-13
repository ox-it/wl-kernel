package org.sakaiproject.util;

import junit.framework.TestCase;

public class WebTest extends TestCase {
	
	public void testBasicUrlMatch() {
		assertEquals("I like <a href=\"http://www.apple.com\">http://www.apple.com</a> and stuff", Web.encodeUrlsAsHtml(Web.escapeHtml("I like http://www.apple.com and stuff")));
	}
	
	public void testCanDoSsl() {
		assertEquals("<a href=\"https://sakaiproject.org\">https://sakaiproject.org</a>", Web.encodeUrlsAsHtml("https://sakaiproject.org"));
	}
	
	public void testCanIgnoreTrailingExclamation() {
		assertEquals("Hey, it's <a href=\"http://sakaiproject.org\">http://sakaiproject.org</a>!", Web.encodeUrlsAsHtml("Hey, it's http://sakaiproject.org!"));
	}
	
	public void testCanIgnoreTrailingQuestion() {
		assertEquals("Have you ever seen <a href=\"http://sakaiproject.org\">http://sakaiproject.org</a>? Just wondering.", Web.encodeUrlsAsHtml("Have you ever seen http://sakaiproject.org? Just wondering."));
	}
	
	public void testCanEncodeQueryString() {
		assertEquals("See <a href=\"http://sakaiproject.org/index.php?task=blogcategory&id=181\">http://sakaiproject.org/index.php?task=blogcategory&amp;id=181</a> for more info.", Web.encodeUrlsAsHtml(Web.escapeHtml("See http://sakaiproject.org/index.php?task=blogcategory&id=181 for more info.")));
	}
	
	public void testCanTakePortNumber() {
		assertEquals("<a href=\"http://localhost:8080/portal\">http://localhost:8080/portal</a>", Web.encodeUrlsAsHtml("http://localhost:8080/portal"));
	}
	
	public void testCanTakePortNumberAndQueryString() {
		assertEquals("<a href=\"http://www.loco.com:3000/portal?person=224\">http://www.loco.com:3000/portal?person=224</a>", Web.encodeUrlsAsHtml("http://www.loco.com:3000/portal?person=224"));
	}
	
	public void testCanIgnoreExistingHref() {
		assertEquals("<a href=\"http://sakaiproject.org\">Sakai Project</a>", Web.encodeUrlsAsHtml("<a href=\"http://sakaiproject.org\">Sakai Project</a>"));
	}
	
	public void testALongUrlFromNyTimes() {
		assertEquals("<a href=\"http://www.nytimes.com/mem/MWredirect.html?MW=http://custom.marketwatch.com/custom/nyt-com/html-companyprofile.asp&symb=LLNW\">http://www.nytimes.com/mem/MWredirect.html?MW=http://custom.marketwatch.com/custom/nyt-com/html-companyprofile.asp&amp;symb=LLNW</a>",
				Web.encodeUrlsAsHtml(Web.escapeHtml("http://www.nytimes.com/mem/MWredirect.html?MW=http://custom.marketwatch.com/custom/nyt-com/html-companyprofile.asp&symb=LLNW")));
	}

	public void testContentDispositionInline() {
		assertEquals("inline; filename=\"file.txt\"; filename*=UTF-8''file.txt",
				Web.buildContentDisposition("file.txt", false));
	}

	public void testContentDispositionAttachment() {
		assertEquals("attachment; filename=\"file.txt\"; filename*=UTF-8''file.txt",
				Web.buildContentDisposition("file.txt", true));
	}

	public void testContentDispositionSemiColon() {
		assertEquals("inline; filename=\"start;stop.txt\"; filename*=UTF-8''start%3Bstop.txt",
				Web.buildContentDisposition("start;stop.txt", false));
	}

	public void testContentDispositionQuotes() {
		assertEquals("inline; filename=\"start\\\"stop.txt\"; filename*=UTF-8''start%22stop.txt",
				Web.buildContentDisposition("start\"stop.txt", false));
	}

	public void testContentDispositionUTF8() {
		// encoding hello world in greek.
		assertEquals("inline; filename=\"???? ??? ?????.txt\"; " +
				"filename*=UTF-8''%CE%93%CE%B5%CE%B9%CE%B1%20%CF%83%CE%B1%CF%82%20%CE%BA%CF%8C%CF%83%CE%BC%CE%BF.txt",
				Web.buildContentDisposition("\u0393\u03B5\u03B9\u03B1 \u03C3\u03B1\u03C2 \u03BA\u03CC\u03C3\u03BC\u03BF.txt", false));
	}

	public void testContentDispositionISO8859() {
		assertEquals("inline; filename=\"exposé.txt\"; filename*=UTF-8''expos%C3%A9.txt",
				Web.buildContentDisposition("exposé.txt", false));
	}
}
