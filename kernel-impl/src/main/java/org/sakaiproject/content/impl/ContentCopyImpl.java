package org.sakaiproject.content.impl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.*;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.*;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InconsistentException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.id.api.IdManager;

public class ContentCopyImpl implements ContentCopy {

	private final static Log log = LogFactory.getLog(ContentCopyImpl.class);

	// The server names which content is served through and so should assumed to be local.
	private Collection<String> servers;

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

	private AuthzGroupService ags;

    private ContentCopyInterceptorRegistry interceptorRegistry;

	private IdManager idManager;
	
	private int autoGeneratedIdLength = 36; // Default Sakai

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
		regexp.append(")[\\s]*=[\\s]*([\"'])?([^\"' #]*)(\\2|#)?");
		attributePattern = Pattern.compile(regexp.toString(),
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		pathPattern = Pattern
				.compile("/(?:access/content/group|web|dav|portal/site)/([^/]+)/.*");

		servers = new ArrayList<String>(scs.getServerNameAliases());
		servers.add(scs.getServerName());
		
		// Incase someone has a custom IdManager.
		if (idManager != null) {
			int length = idManager.createUuid().length();
			if (length != idManager.createUuid().length()) {
				log.warn("IdManager doesn't generate consistent length IDs.");
			} else {
				autoGeneratedIdLength = length;
			}
		}
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

	public void setAuthzGroupService(AuthzGroupService ags) {
		this.ags = ags;
	}

	public void setIdManager(IdManager idManager) {
		this.idManager = idManager;
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
				newResource.setResourceType(resource.getResourceType());
				newResource.setAvailability(resource.isHidden(), resource.getReleaseDate(), resource.getRetractDate());
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
				chs.commitResource(newResource, NotificationService.NOTI_NONE);
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
				context.logCopy(resourceId, newResourceId);
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
			try {

				newCollection = chs.addCollection(newCollectionId);
				// We don't want to read large amounts of HTML into memory.
				ResourcePropertiesEdit propsEdit = newCollection
						.getPropertiesEdit();
				propsEdit.clear();
				propsEdit.addAll(resource.getProperties());
				// Copy across the availability information
				newCollection.setAvailability(resource.isHidden(), resource.getReleaseDate(), resource.getRetractDate());

				// Copy the permissions across
				try{
					AuthzGroup oldRealm = ags.getAuthzGroup(resource.getReference());
					ags.addAuthzGroup(newCollection.getReference(), oldRealm, null);
				} catch (GroupNotDefinedException e) {
					// do nothing - this case is expected to be common
				} catch (GroupAlreadyDefinedException e) {
					log.warn("A realm is already defined for new collection: " + newCollectionId);
				} catch (GroupIdInvalidException e) {
					log.warn("A realm is already defined for new collection: " + newCollectionId);
				} catch (AuthzPermissionException e) {
					log.warn("Did not have permission to set Realm for the new collection: "+ newCollectionId);
				}

				chs.commitCollection(newCollection);
				context.logCopy(collectionId, newCollectionId);
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
				} else if ("text/plain".equals(mimeType)) {
					return convertTextContent(context, content, contentUrl);
				}
			} catch (Exception e) {
				// Don't have the copy fail but log it and carry on.
				log.warn("Failed to convert content, returning original.", e);
			}
		}
		return content;
	}

	private String convertTextContent(ContentCopyContext context, String content, String contentUrl) {
		if (context.getOldSiteId().length() == autoGeneratedIdLength) {
			for (ContentCopyTextInterceptor textInterceptor : interceptorRegistry.getTextInterceptors()) {
				content = textInterceptor.runTextInterceptor(content, Collections.singletonMap(context.getOldSiteId(), context.getNewSiteId()));
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
		// If it's a a30aa758-a651-4d18-90a3-739a2d2de65f type site ID.
		String outputedString = output.toString();
		if (context.getOldSiteId().length() == autoGeneratedIdLength) {
			for(ContentCopyTextInterceptor textInterceptor: interceptorRegistry.getTextInterceptors()){
				outputedString = textInterceptor.runTextInterceptor(outputedString, Collections.singletonMap(context.getOldSiteId(), context.getNewSiteId()));
			}
		}
		return outputedString;
	}

	/**
	 * Takes a URL and then decides if it should be replaced.
	 * 
	 * @param value
	 * @return
	 */
	private String processUrl(ContentCopyContext context, String value,
			String contentUrl) {
        //Transform the URL if required:
        ContentCopyUrlInterceptor urlInterceptor = interceptorRegistry.getUrlInterceptor(value);
        if(urlInterceptor != null){
            String convertedUrl = urlInterceptor.convertUrl(value);
            String processedUrl = processUrl(context, convertedUrl, contentUrl);
            return urlInterceptor.convertProcessedUrl(processedUrl);
        }

		// Need to deal with backticks.
		// - /access/group/{siteId}/
		// - /web/{siteId}/
		// - /dav/{siteId}/
		// http(s)://weblearn.ox.ac.uk/ - needs trimming
		try {
			URI uri = new URI(value);
			uri = uri.normalize();
			if (isRelativeUri(uri) || isLocalUri(uri)) {
				// Need to attempt todo path replacement now.
				String path = uri.getPath();
				Matcher matcher = pathPattern.matcher(path);
				if (matcher.matches() && context.getOldSiteId().equals(matcher.group(1))) {
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
     * Checks whether an URI is local to the current server (and is in https or http) or not.
     *
     * @param uri URI to check
     * @return true if the hostname in the URI is the current server and if the protocol is HTTP or HTTPS
     */
    private boolean isLocalUri(URI uri) {
        return (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && servers.contains(uri.getHost()));
    }

    /**
     * Checks whether an URI is relative or not.
     *
     * @param uri URI to check
     * @return true if the URI is relative (no hostname but a path is provided), false otherwise
     */
    private boolean isRelativeUri(URI uri) {
        return (uri.getHost() == null && uri.getPath() != null);
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
	 * @param resourceId
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

    public void setInterceptorRegistry(ContentCopyInterceptorRegistry interceptorRegistry) {
        this.interceptorRegistry = interceptorRegistry;
    }
}
