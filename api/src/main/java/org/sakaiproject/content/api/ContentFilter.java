package org.sakaiproject.content.api;

import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

/**
 * Interface that allows modification of some content.
 * We don't have access to the response headers so can't do things like zip compression.
 * @author buckett
 *
 */
public interface ContentFilter {

	/**
	 * Check if this content filter should be applied to the resource. This should be a fast 
	 * check.
	 * @param resource The resource being requested.
	 * @return <code>true</code> if a filter should be retrieved using {@link #wrap(OutputStream)}.
	 */
	public boolean isFiltered(ContentResource resource);
	
	/**
	 * Create a filter which will process the content.
	 * @param content A stream to which the output will be written.
	 * @return An output stream which is contains the modified output.
	 */
	public HttpServletResponse wrap(HttpServletResponse response, ContentResource resource);
	
}
