package org.sakaiproject.content.impl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentCopy;
import org.sakaiproject.content.api.ContentCopyContext;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InconsistentException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;

public class ContentCopyImpl implements ContentCopy {

	private final static Log log = LogFactory.getLog(ContentCopyImpl.class);

	// The server names which content is served through and so should assumed to be local.
	private Set<String> servers;

	// The maximum size of content we filter and update references for.
	private long maxContentSize = 5 * 1024 * 1024; // 5MB

	// The attributes in HTML that should have their values looked at and possibly re-written
	private Collection<String> attributes = new HashSet<String>(
			Arrays.asList(new String[] { "href", "src", "background", "action",
					"pluginspage", "pluginurl", "classid", "code", "codebase",
					"data", "usemap" }));
	
	private Pattern attributePattern;

	private Pattern pathPattern;

	private ContentHostingService chs;

	private ServerConfigurationService scs;

	public void init() {
		// Builds a Regexp selector.
		StringBuilder regexp = new StringBuilder("(");
		for (String attribute : attributes) {
			regexp.append(attribute);
			regexp.append("|");
		}
		if (regexp.length() > 1) {
			regexp.deleteCharAt(regexp.length() - 1);
		}
		regexp.append(")[\\s]*=[\\s]*([\"'|])([^\"']*)(\\2|#)");
		attributePattern = Pattern.compile(regexp.toString(),
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		pathPattern = Pattern
				.compile("/(?:access/content/group|web|dav|portal/site)/([^/]+)/.*");

		// Add the server name to the list of servers
		String serverName = scs.getServerName();
		if (scs != null && serverName != null) {
			if (servers == null) {
				servers = Collections.singleton(serverName);
			} else {
				servers.add(serverName);
			}
		}
	}

	public void setServers(Set<String> servers) {
		this.servers = servers;
	}

	public void setMaxContentSize(long maxContentSize) {
		this.maxContentSize = maxContentSize;
	}

	public void setContentHostingService(ContentHostingService chs) {
		this.chs = chs;
	}

	public void setServerConfigurationService(ServerConfigurationService scs) {
		this.scs = scs;
	}

	public ContentCopyContext createCopyContext(String oldSiteId,
			String newSiteId, boolean walkReferences) {
		return new ContentCopyContextImpl(oldSiteId, newSiteId, walkReferences);
	}

	public void copyReferences(ContentCopyContext context) {
		for (String resourceId = context.popResource(); resourceId != null; resourceId = context
				.popResource()) {
			boolean isCollection = chs.isCollection(resourceId);
			if (isCollection) {
				copyCollection(context, resourceId);
			} else {
				copyResource(context, resourceId);
			}
		}
	}

	private void copyResource(ContentCopyContext context, String resourceId) {
		try {
			// This shouldn't assume that the resources come from the old site
			// defined in the context.

			ContentResource resource = chs.getResource(resourceId);

			String siteCollectionId = getSiteCollection(resourceId);
			String newResourceId = "/group/" + context.getNewSiteId() + "/"
					+ resourceId.substring(siteCollectionId.length());

			// Now create the copy.
			ContentResourceEdit newResource = null;
			boolean success = false;
			try {

				newResource = chs.addResource(newResourceId);
				newResource.setContentType(resource.getContentType());
				// We don't want to read large amounts of HTML into memory.
				if ("text/html".equals(resource.getContentType())
						&& resource.getContentLength() < maxContentSize) {
					String content = new String(resource.getContent(), "UTF-8");
					String convertedContent = convertContent(context, content,
							resource.getContentType(), resource.getUrl());
					newResource.setContent(convertedContent.getBytes("UTF-8"));
				} else {
					newResource.setContent(resource.streamContent());
				}

				ResourcePropertiesEdit propsEdit = newResource
						.getPropertiesEdit();
				propsEdit.clear();
				propsEdit.addAll(resource.getProperties());
				chs.commitResource(newResource);
				success = true;
			} catch (PermissionException e) {
				log.warn("User doesn't have permission to create resource: "
						+ newResourceId);
			} catch (IdUsedException e) {
				log.warn("Couldn't copy resource a new location already exists: "
						+ newResourceId);
			} catch (IdInvalidException e) {
				log.warn("Incorrect formatting of the resource ID: "
						+ newResourceId);
			} catch (InconsistentException e) {
				log.warn("Something is inconsistent when creating: "
						+ newResourceId);
			} catch (ServerOverloadException e) {
				log.warn("Server is overloaded when creating: " + newResourceId);
			} catch (OverQuotaException e) {
				log.warn("Quota has run out when creating: " + newResourceId);
				throw new RuntimeException("No quota remains", e);
			} catch (UnsupportedEncodingException e) {
				log.fatal("For some reason we have no " + "UTF-8");
				throw new RuntimeException("Character Encoding unknown: "
						+ "UTF-8");
			} finally {
				context.logCopy(resourceId, success);
				// If something went wrong cancel the edit.
				if (newResource != null && newResource.isActiveEdit()) {
					chs.cancelResource(newResource);
				}
			}
		} catch (IdUnusedException iue) {
			log.warn("Failed to find resource to copy: " + resourceId);
		} catch (TypeException e) {
			log.info("Ignoring resource which is a collection:" + resourceId);
		} catch (PermissionException e) {
			log.warn("User isn't able to access resource to be copied: "
					+ resourceId);
		}
	}

	private void copyCollection(ContentCopyContext context, String collectionId) {
		try {
			// This shouldn't assume that the resources come from the old site
			// defined in the context.

			ContentCollection resource = chs.getCollection(collectionId);

			String siteCollectionId = getSiteCollection(collectionId);
			String newCollectionId = "/group/" + context.getNewSiteId() + "/"
					+ collectionId.substring(siteCollectionId.length());

			// Now create the copy.
			ContentCollectionEdit newCollection = null;
			// Was the copy successful.
			boolean success = false;
			try {

				newCollection = chs.addCollection(newCollectionId);
				// We don't want to read large amounts of HTML into memory.
				ResourcePropertiesEdit propsEdit = newCollection
						.getPropertiesEdit();
				propsEdit.clear();
				propsEdit.addAll(resource.getProperties());
				chs.commitCollection(newCollection);
				success = true;
			} catch (PermissionException e) {
				log.warn("User doesn't have permission to create resource: "
						+ newCollectionId);
			} catch (IdUsedException e) {
				log.warn("Couldn't copy resource a new location already exists: "
						+ newCollectionId);
			} catch (IdInvalidException e) {
				log.warn("Incorrect formatting of the resource ID: "
						+ newCollectionId);
			} catch (InconsistentException e) {
				log.warn("Something is inconsistent when creating: "
						+ newCollectionId);
			} finally {
				context.logCopy(collectionId, success);
				// If something went wrong cancel the edit.
				if (newCollection != null && newCollection.isActiveEdit()) {
					chs.cancelCollection(newCollection);
				}
			}
		} catch (IdUnusedException iue) {
			log.warn("Failed to find resource to copy: " + collectionId);
		} catch (TypeException e) {
			log.info("Ignoring resource which is a collection:" + collectionId);
		} catch (PermissionException e) {
			log.warn("User isn't able to access resource to be copied: "
					+ collectionId);
		}
	}

	public String convertContent(ContentCopyContext context, String content,
			String mimeType, String contentUrl) {
		// Just return null if passed null
		if (content != null) {
			try {
				if (mimeType == null || "text/html".equals(mimeType)) {
					return convertHtmlContent(context, content, contentUrl);
				}
			} catch (Exception e) {
				// Don't have the copy fail but log it and carry on.
				log.warn("Failed to convert content, returning original.", e);
			}
		}
		return content;
	}

	private String convertHtmlContent(ContentCopyContext context,
			String content, String contentUrl) {
		StringBuilder output = new StringBuilder();
		Matcher matcher = attributePattern.matcher(content);
		int contentPos = 0;
		while (matcher.find()) {
			String url = matcher.group(3);

			url = processUrl(context, url, contentUrl);
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

	/**
	 * Takes a URL and then decides if it should be replaced.
	 * 
	 * @param value
	 * @return
	 */
	private String processUrl(ContentCopyContext context, String value,
			String contentUrl) {
		// Need to deal with backticks.
		// - /access/group/{siteId}/
		// - /web/{siteId}/
		// - /dav/{siteId}/
		// http(s)://weblearn.ox.ac.uk/ - needs trimming
		try {
			URI uri = new URI(value);
			uri = uri.normalize();
			if ("http".equals(uri.getScheme())
					|| "https".equals(uri.getScheme())) {
				if (uri.getHost() != null) {
					if (servers.contains(uri.getHost())) {
						// Drop the protocol and the host.
						uri = new URI(null, null, null, -1, uri.getPath(),
								uri.getQuery(), uri.getFragment());
					}
				}
			}
			// Only do replacement on our URLs.
			if (uri.getHost() == null && uri.getPath() != null) {
				// Need to attempt todo path replacement now.
				String path = uri.getPath();
				Matcher matcher = pathPattern.matcher(path);
				if (matcher.matches()
						&& context.getOldSiteId().equals(matcher.group(1))) {
					// Need to push the old URL onto the list of resources to
					// process.
					addPath(context, path);
					String replacementPath = path
							.substring(0, matcher.start(1))
							+ context.getNewSiteId()
							+ path.substring(matcher.end(1));
					// Create a new URI with the new path
					uri = new URI(uri.getScheme(), uri.getUserInfo(),
							uri.getHost(), uri.getPort(), replacementPath,
							uri.getQuery(), uri.getFragment());
				} else if (!path.startsWith("/") && contentUrl != null) {
					// Relative URL.
					try {
						URI base = new URI(contentUrl);
						URI link = base.resolve(uri);
						addPath(context, link.getPath());
					} catch (URISyntaxException e) {
						log.info("Supplied contentUrl isn't valid: "
								+ contentUrl);
					}
				}
			}
			return uri.toString();
		} catch (URISyntaxException e) {
			// Log this so we may get an idea of the things that are breaking
			// the parser.
			log.info("Failed to parse URL: " + value + " " + e.getMessage());
		}
		return value;
	}

	/**
	 * Small helper method which adds a content hosting ID to the list of ones
	 * to be processed based on the web URL of the resource.
	 */
	private void addPath(ContentCopyContext context, String path) {
		if (context.isWalkReferences()) {
			if (path.startsWith("/access/content")) {
				context.addResource(path.substring("/access/content".length()));
			}
			if (path.startsWith("/web")) {
				context.addResource(path.substring("/web".length()));
			}
		}
	}

	/**
	 * This just finds the site collection for a resource. It just walks up the
	 * tree.
	 * 
	 * @param resource
	 *            The resource.
	 * @return The site collection which contains this resource.
	 */
	String getSiteCollection(String resourceId) {
		int pos = 0;
		int match = 0;
		while (++match < 3 && pos >= 0) {
			pos = resourceId.indexOf('/', pos + 1);
		}
		if (match == 3) {
			String collectionId = resourceId.substring(0, pos + 1);
			return collectionId;
		}
		return null;
	}

}
