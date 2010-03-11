package org.sakaiproject.content.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simple InputStream that just reads the first few bytes of the wrapped stream and if it
 * looks like HTML wrap it with a header and footer. 
 * This is split out from WrappedContentResource as it's easier to test a input stream without 
 * any dependency on the Sakai classes.
 * 
 * @author Matthew Buckett
 *
 */
public class WrappedInputStream extends InputStream {

	private InputStream wrapped;
	private String header;
	private String footer;
	
	private boolean wrapping = false;
	private boolean detect = false;
	private boolean checked = false;
	
	public WrappedInputStream(InputStream wrapped, String header, String footer, boolean detect) {
		this.wrapped = wrapped;
		this.header = header;
		this.footer = footer;
		this.detect = detect; 
	}
	
	private void check() throws IOException {
		// Shortcut.
		if (checked) {
			return;
		}
		checked = true;
		if (detect) {
			byte[] buffer = new byte[128];
			int read = wrapped.read(buffer);
			if (read > 0) {
				// TODO Charset problems?
				String bufferString = new String(buffer);
				// This is the test we do.
				wrapping = !bufferString.toLowerCase().contains("<html");
				if (wrapping) {
					wrapped = new ChainedInputStream(
							new ByteArrayInputStream(header.getBytes()),
							new ByteArrayInputStream(buffer,0,read),
							wrapped,
							new ByteArrayInputStream(footer.getBytes())
							);
				} else {
					// Push the content back to be read.
					wrapped = new ChainedInputStream(new ByteArrayInputStream(buffer, 0, read), wrapped);
				}
			}
		} else {
			// Always add header and footer.
			wrapping = true;
			wrapped = new ChainedInputStream(new ByteArrayInputStream(header.getBytes()), wrapped, new ByteArrayInputStream(footer.getBytes()));
		}

	}
	
	/**
	 * Are we wrapping the existing stream?
	 * @return
	 */
	public boolean isWrapping() throws IOException {
		check();
		return  wrapping;
	}
	
	/**
	 * Returns the number of extra bytes we are adding to the stream.
	 * @return 0 or the size in bytes of the header and footer.
	 */
	public long getExtraLength() throws IOException {
		check();
		if (isWrapping()) {
			// As we're in UTF-8 land, we need to make sure count the bytes, not the character.
			return header.getBytes().length+footer.getBytes().length;
		} else {
			return 0;
		}
	}
	
	@Override
	public int read() throws IOException {
		check();
		return wrapped.read();
	}
	
	@Override
	public int read(byte[] bytes) throws IOException {
		check();
		return wrapped.read(bytes);
	}
	
	@Override
	public int read(byte[] bytes, int offset, int length) throws IOException {
		check();
		return wrapped.read(bytes, offset, length);
	}
	
	@Override
	public long skip(long bytes) throws IOException {
		check();
		return wrapped.skip(bytes);
	}
	
	@Override
	public int available() throws IOException {
		check();
		return wrapped.available();
	}
	
	@Override
	public void close() throws IOException {
		wrapped.close();
	}
}
