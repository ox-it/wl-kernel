package org.sakaiproject.content.impl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Class to keep track of how the copying of resources is going.
 * @author buckett
 *
 */
public class CopyContext {

	private String oldSiteId;
	private String newSiteId;
	
	private boolean walkReferences;
	
	private Set<String> resourcesToProcess = new HashSet<String>();
	private Set<String> resourcesProcessed = new HashSet<String>();
	
	public CopyContext(String oldSiteId, String newSiteId, boolean walkReferences) {
		this.oldSiteId = oldSiteId;
		this.newSiteId = newSiteId;
		this.walkReferences = walkReferences;
	}

	public String getOldSiteId() {
		return oldSiteId;
	}

	public String getNewSiteId() {
		return newSiteId;
	}

	public boolean isWalkReferences() {
		return walkReferences;
	}

	/**
	 * When a new resource is found is should be normalised and passed to this method.
	 * @param resource
	 */
	public void addResource(String resource) {
		if (!resourcesProcessed.contains(resource)) {
			resourcesToProcess.add(resource);
		}
	}
	
	/**
	 * When a new resource to be process is needed this method should be called.
	 * If we aren't walking the references then we never give them back.
	 * @return
	 */
	public String popResource() {
		Iterator<String> it = resourcesToProcess.iterator();
		if (walkReferences && it.hasNext()) {
			String resource = it.next();
			resourcesProcessed.add(resource);
			return resource;
		}
		return null;
	}
}
