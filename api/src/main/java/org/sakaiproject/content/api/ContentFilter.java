package org.sakaiproject.content.api;


/**
 * Interface that allows modification of some content.
 * Originally the servlet output stream was wrapped, but wrapping the
 * ContentResource allows ranged gets to still work.
 * 
 * @author Matthew Buckett
 *
 */
public interface ContentFilter {

	/**
	 * Create a filter which will process the content.
	 * @param content A stream to which the output will be written.
	 * @return An output stream which is contains the modified output.
	 */
	public ContentResource wrap(ContentResource resource);
	
}
