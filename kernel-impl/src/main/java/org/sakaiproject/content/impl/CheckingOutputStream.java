package org.sakaiproject.content.impl;

import java.io.IOException;

import javax.servlet.ServletOutputStream;

/**
 * ServletOutputStream that buffers the first few bytes to try and check if it looks like a HTML document,
 * if it does then it prepends a header and tags on a footer.
 * 
 * Once detection of HTML has been done it just passes all calls through the the underlying OutputStream.
 * @author buckett
 *
 */
public class CheckingOutputStream extends ServletOutputStream {
	
	private final String header;
	private final String footer;
	private final ServletOutputStream wrapped;
	
	// Stash the first few bytes so we can check if it looks like it already has HTML header...
	private byte[] bufferedHeader = new byte[256];
	private int bufferPos = 0;
	/** Are we checking writes for HTML at the moment */
	private boolean checking = true;
	/** Are we going to wrap the output. */
	private boolean wrapping = false;

	public CheckingOutputStream(String header, String footer, ServletOutputStream wrapped) {
		this.header = header;
		this.footer = footer;
		this.wrapped = wrapped;
	}

	public void close() throws IOException {
		try {
			if (checking) {
				checking = false;
				checkBuffer();
				writeHeader();
				writeBuffer();
			}
			writeFooter();
			wrapped.flush();
		} finally {
			wrapped.close();
		}
	}

	public void write(int b) throws IOException {
		// We only do checking here as non custom ServletOutputStream instances push all data through here.
		if (checking) {
			if (bufferPos < bufferedHeader.length) {
				bufferedHeader[bufferPos++] = (byte) ((b << 24) >> 24); // Drop all but the last 8 bits
			} else {
				checking = false;
				checkBuffer();
				writeHeader();
				writeBuffer();
				wrapped.write(b);
			}
		} else {
			wrapped.write(b);
		}
	}

	private void checkBuffer() {
		String buffer = new String(bufferedHeader);
		buffer = buffer.toLowerCase();
		wrapping = !buffer.contains("<html");
	}

	private void writeBuffer() throws IOException {
		for (int pos = 0; pos < bufferPos; pos++) {
			wrapped.write(bufferedHeader[pos]);
		}
		bufferPos = 0;
		bufferedHeader = null;
	}

	private void writeHeader() throws IOException {
		if (wrapping) {
			wrapped.print(header);
		}
	}

	private void writeFooter() throws IOException {
		if (wrapping) {
			wrapped.print(footer);
		}
	}

	public void print(boolean b) throws IOException {
		if (checking) {
			super.print(b);
		} else {
			wrapped.print(b);
		}
	}

	public void print(char c) throws IOException {
		if (checking) {
			super.print(c);
		} else {
			wrapped.print(c);
		}
	}

	public void print(double d) throws IOException {
		if (checking) {
			super.print(d);
		} else {
			wrapped.print(d);
		}
	}

	public void print(float f) throws IOException {
		if (checking) {
			super.print(f);
		} else {
			wrapped.print(f);
		}
	}

	public void print(int i) throws IOException {
		if (checking) {
			super.print(i);
		} else {
			wrapped.print(i);
		}
	}

	public void print(long l) throws IOException {
		if (checking) {
			super.print(l);
		} else {
			wrapped.print(l);
		}
	}

	public void print(String s) throws IOException {
		if (checking) {
			super.print(s);
		} else {
			wrapped.print(s);
		}
	}

	public void println() throws IOException {
		if (checking) {
			super.println();
		} else {
			wrapped.println();
		}
	}

	public void println(boolean b) throws IOException {
		if (checking) {
			super.println(b);
		} else {
			wrapped.println(b);
		}
	}

	public void println(char c) throws IOException {
		if (checking) {
			super.println(c);
		} else {
			wrapped.println(c);
		}
	}

	public void println(double d) throws IOException {
		if (checking) {
			super.println(d);
		} else {
			wrapped.println(d);
		}
	}

	public void println(float f) throws IOException {
		if (checking) {
			super.println(f);
		} else {
			wrapped.println(f);
		}
	}

	public void println(int i) throws IOException {
		if (checking) {
			super.println(i);
		} else {
			wrapped.println(i);
		}
	}

	public void println(long l) throws IOException {
		if (checking) {
			super.println(l);
		} else {
			wrapped.println(l);
		}
	}

	public void println(String s) throws IOException {
		if (checking) {
			super.println(s);
		} else {
			wrapped.println(s);
		}
	}

	public void write(byte[] b, int off, int len) throws IOException {
		if (checking) {
			super.write(b, off, len);
		} else {
			wrapped.write(b, off, len);
		}
	}

	public void write(byte[] b) throws IOException {
		if (checking) {
			super.write(b);
		} else {
			wrapped.write(b);
		}
	}
}