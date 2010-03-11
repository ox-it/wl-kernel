package org.sakaiproject.content.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.exception.ServerOverloadException;

/**
 * This class wraps a content resource with a header and footer.
 * Obviously this only makes sense for files such as HTML.
 * @author Matthew Buckett
 *
 */
public class WrappedContentResource extends AbstractContentResource {
	
	private String header;
	private String footer;
	private boolean detect;
	// After returning the stream, null it out. This means a new copy is returned the next time.
	private WrappedInputStream stream;
	
	public WrappedContentResource(ContentResource resource, String header, String footer, boolean detect) {
		super(resource);
		this.header = header;
		this.footer = footer;
		this.detect = detect;
	}

	private WrappedInputStream setupStream() throws ServerOverloadException{
		if (stream == null) {
			InputStream wrapped = super.streamContent();
			stream = new WrappedInputStream(wrapped, header, footer, detect);
		}
		return stream;
	}
	
	public byte[] getContent() throws ServerOverloadException {
		try {
			byte[] bytes = IOUtils.toByteArray(streamContent());
			stream = null;
			return bytes;
		} catch (IOException e) {
			throw new ServerOverloadException("IO propblem: "+ e.getMessage());
		}
	}
	
	public InputStream streamContent() throws ServerOverloadException {
		InputStream stream = setupStream();
		this.stream = null;
		return stream;
	}
	
	public long getContentLength() {
		long extraLength = 0;
		try {
			setupStream();
			extraLength = stream.getExtraLength();
		} catch (ServerOverloadException soe) {
			// Ignore as things will probably break later on.
		} catch (IOException e) {
			// Ignore as things will probably break later on.
		}
		return super.getContentLength()+ extraLength;
	}
	
}
