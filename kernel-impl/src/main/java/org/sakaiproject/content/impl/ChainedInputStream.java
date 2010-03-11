package org.sakaiproject.content.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;


/**
 * Processes a chain of InputStreams reading from one after the other.
 * It's a bit like cat(1) from the UNIX world.
 */
public class ChainedInputStream extends InputStream {

	private Queue<InputStream> stack;
	private InputStream current;
	
	public ChainedInputStream(InputStream... streams) {
		this.stack = new LinkedList<InputStream>();
		this.stack.addAll(Arrays.asList(streams));
		current = this.stack.poll();
	}
	
	/**
	 * This looks to see if there is another stream to read from and if so it
	 * sets it to the current stream.
	 * @return
	 * @throws IOException If the exhausted InputStream can't be closed.
	 */
	private boolean hasStream() throws IOException {
		if (!stack.isEmpty()) {
			current.close();
			current = stack.poll();
			return true;
		}
		return false;
	}
	
	@Override
	public int read() throws IOException {
		int val;
		do {
			val = current.read();
		} while(val < 0 && hasStream());
		return val;
	}
	
	@Override
	public int read(byte[] bytes) throws IOException {
		return read(bytes, 0, bytes.length);
	}
	
	@Override
	public int read(byte[] bytes, int offset, int len) throws IOException {
		int total = 0;
		boolean goodRead = false;
		do {
			int read = current.read(bytes, offset+total, len-total);
			if (read >= 0) {
				goodRead = true;
				total += read;
			}
		} while(total < len && hasStream());
		return (goodRead)?total:-1;
	}
	
	@Override
	public long skip(long skip) throws IOException {
		long skipped = 0;
		do {
			skipped += current.skip(skip-skipped);
		} while (skipped < skip && hasStream());
		return skipped;
	}
	
	@Override
	public int available() throws IOException {
		return current.available();
	}
	
	@Override
	public void close() throws IOException {
		do {
			current.close();
		} while(hasStream());
	}
}
