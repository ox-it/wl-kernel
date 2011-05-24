package org.sakaiproject.content.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import edu.emory.mathcs.backport.java.util.Arrays;

public class ContentCopy {
	
	private Collection<String> servers;
	
	static Collection<String> attributes = new HashSet<String>(Arrays.asList(new String[]{"href","src","background","action","pluginspage","pluginurl","classid","code","codebase","data","usemap"}));
	static String attributeSelector;
	static Pattern attributePattern;
	
	static {
		// Builds a Jsoup selector.
		StringBuilder selector = new StringBuilder();
		for (String attribute: attributes) {
			selector.append("[");
			selector.append(attribute);
			selector.append("],");
		}
		if (selector.length() >1) {
			selector.deleteCharAt(selector.length()-1);
		}
		// Builds a Regexp selector.
		StringBuilder regexp = new StringBuilder("(");
		for (String attribute: attributes) {
			regexp.append(attribute);
			regexp.append("|");
		}
		if (regexp.length() > 1) {
			regexp.deleteCharAt(regexp.length()-1);
		}
		// We can stop at the anchor as we're just interested in paths.
		regexp.append(")[\\s]*=[\\s]*([\"'|])([^#\"']*)(\\2|#)");
		attributePattern = Pattern.compile(regexp.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

		attributeSelector = selector.toString();
	}
	
	//Special case "archive" (multiple, space separated)
	
	public CopyContext createCopyContext(String oldSiteId, String newSiteId, boolean walkReferences) {
		return null; // TODO Factory for when we extract the API.
	}
	
	/**
	 * This copies all the references defined in the context from one site to another.
	 * @param context
	 * @return
	 */
	public String copyReferences(CopyContext context) {
		return null;
	}
	
	/**
	 * This looks for all the references in the supplied content and returns the re-written string.
	 * It also updates the supplied content and returns it.
	 * This can be use to initially setup the references if copying a piece of HTML content with embedded references.
	 * @param context
	 * @param mimeType The mime type of the supplied content.
	 * @return
	 */
	public String convertContent(CopyContext context, String content, String mimeType) {
		
		if (mimeType == null || "text/html".equals(mimeType)) {
			return convertHtmlContent(context, content);
		}
		return content;
	}

	private String convertHtmlContent(CopyContext context, String content) {
		StringBuilder output = new StringBuilder();
		Matcher matcher = attributePattern.matcher(content);
		int contentPos = 0;
		while (matcher.find()) {
			String url = matcher.group(3);
			
			url = processUrl(context, url);
			// Content up to the match.
			int copyTo = matcher.start(3);
			// Start the second copy after the match.
			int copyFrom = matcher.end(3);
			int copyEnd = matcher.end();
			
			output.append(content.substring(contentPos, copyTo));
			output.append(url);
			output.append(content.substring(copyFrom, copyEnd));
			contentPos = copyEnd;
		}
		output.append(content.substring(contentPos));
		return output.toString();
	}

	private String convertHtmlContentJSoup(CopyContext context, String content) {
		
		// Need to decide if this is a fragment or full HTML, this is so what we know to serialise back out.
		Document document = Jsoup.parseBodyFragment(content);
		document.outputSettings().prettyPrint(false);
		for (Element element: document.select(attributeSelector)) {
			for (Attribute attribute: element.attributes()) {
				if (attributes.contains(attribute)) {
					String updatedUrl = processUrl(context, attribute.getValue());
					if (updatedUrl != null) {
						element.attr(attribute.getKey(), updatedUrl);
					}
				}
			}
		}
		return document.body().html();
	}

	/**
	 * Takes a URL and then decides if it should be replaced.
	 * @param value
	 * @return
	 */
	private String processUrl(CopyContext context, String value) {
		// Need to deal with backticks.
		// - /access/group/{siteId}/
		// - /web/{siteId}/
		// - /dav/{siteId}/
		// http(s)://weblearn.ox.ac.uk/ - needs trimming
		try {
			URL url = new URL(value);
			if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) {
				if (url.getHost() != null){
					
				}
					
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return value;
	}
	
	private Collection<String> getServers() {
		return null;
	}

}
