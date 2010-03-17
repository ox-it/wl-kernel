package org.sakaiproject.content.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletOutputStream;

import junit.framework.TestCase;

public class WrappedServletOutputStreamTest extends TestCase {

	public void testSimpleOutput() throws IOException {
		ByteArrayOutputStream array = new ByteArrayOutputStream();
		ServletOutputStream servletStream = getServletOutputStream(array);
		WrappedServletOutputStream stream = new WrappedServletOutputStream("header ", " footer", servletStream);
		stream.print("hello");
		stream.close();
		assertEquals("header hello footer", new String(array.toByteArray()));
	}
	
	
	private ServletOutputStream getServletOutputStream(final OutputStream array) {
		ServletOutputStream servletStream = new ServletOutputStream() {
			
			@Override
			public void write(int b) throws IOException {
					array.write(b);
				
			}
		};
		return servletStream;
	}
}
