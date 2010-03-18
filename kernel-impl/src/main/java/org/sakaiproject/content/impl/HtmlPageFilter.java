package org.sakaiproject.content.impl;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.content.api.ContentFilter;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.util.Validator;

/**
 * Simple filter that adds header and footer fragments to HTML pages, it can detect
 * to add HTML or be forced to/not.
 * 
 * @author buckett
 *
 */
public class HtmlPageFilter implements ContentFilter {

	private EntityManager entityManager;
	
	private ServerConfigurationService serverConfigurationService;
	
	/** If <code>false</false> then this filter is disabled. */
	private boolean enabled = true;
	
	private String headerTemplate = 
"<html>\n" +
"  <head>\n" +
"    <meta http-equiv=\"Content-Style-Type\" content=\"text/css\" /> \n" +
"    <title>{2}</title>\n" +
"    <link href=\"{0}/tool_base.css\" type=\"text/css\" rel=\"stylesheet\" media=\"all\" />\n" +
"    <link href=\"{0}/{1}/tool.css\" type=\"text/css\" rel=\"stylesheet\" media=\"all\" />\n" +
"    <script type=\"text/javascript\" language=\"JavaScript\" src=\"/library/js/headscripts.js\"></script>\n" +
"    <style>body '{ padding: 5px !important; }'</style>\n" +
"  </head>\n" +
"  <body>\n";
	
	private String footerTemplate = "\n" +
"  </body>\n" +
"</html>\n";

	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}
	
	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public void setHeaderTemplate(String headerTemplate) {
		this.headerTemplate = headerTemplate;
	}

	public void setFooterTemplate(String footerTemplate) {
		this.footerTemplate = footerTemplate;
	}

	public boolean isFiltered(ContentResource resource) {
		String addHtml = resource.getProperties().getProperty(ResourceProperties.PROP_ADD_HTML);
		return enabled && ("text/html".equals(resource.getContentType())) && ((addHtml == null) || (!addHtml.equals("no") || addHtml.equals("yes")));
	}

	public HttpServletResponse wrap(final HttpServletResponse response, final ContentResource content) {
		Reference contentRef = entityManager.newReference(content.getReference());
		Reference siteRef = entityManager.newReference(contentRef.getContext());
		Entity entity = siteRef.getEntity();
		
		String addHtml = content.getProperties().getProperty(ResourceProperties.PROP_ADD_HTML);
		// Assume we want the filter.
		final boolean detectHtml = addHtml == null || addHtml.equals("auto");
		
		String skinRepo = getSkinRepo();
		String siteSkin = getSiteSkin(entity);
		String title = getTitle(content);
		final String header = MessageFormat.format(headerTemplate, skinRepo, siteSkin, title);
		final String footer = footerTemplate;
		
		return new HttpServletResponseWrapper(response) {

			public void setContentLength(int length) {
				// Add on the size of our header and footer.
				// We can't be sure that we're going to add the header.
				// super.setContentLength(length + header.getBytes().length + footer.getBytes().length);
			}
			
			public ServletOutputStream getOutputStream() throws IOException {
				final ServletOutputStream wrapped = response.getOutputStream();
				if (detectHtml) {
					return new CheckingOutputStream(header, footer, wrapped);
				} else {
					return new WrappedServletOutputStream(header, footer, wrapped);
				}
			}
			
		};
	}

	private String getTitle(final ContentResource content) {
		String title = content.getProperties().getProperty(ResourceProperties.PROP_DISPLAY_NAME);
		if (title == null) {
			title = content.getId();
		}
		return Validator.escapeHtml(title);
	}

	private String getSkinRepo() {
		final String skinRepo = serverConfigurationService.getString("skin.repo", "/library/skins");
		return skinRepo;
	}

	private String getSiteSkin(Entity entity) {
		String siteSkin = serverConfigurationService.getString("skin.default", "default"); 
		if (entity instanceof Site) {
			Site site =(Site)entity;
			if (site.getSkin() != null && site.getSkin().length() > 0) {
				siteSkin = site.getSkin();
			}
		}
		return siteSkin;
	}

}
