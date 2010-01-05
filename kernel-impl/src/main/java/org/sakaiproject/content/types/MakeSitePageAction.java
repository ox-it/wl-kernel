/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2006, 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.content.types;

import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ResourceToolAction;
import org.sakaiproject.content.api.ServiceLevelAction;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.util.Resource;
import org.sakaiproject.util.ResourceLoader;

public class MakeSitePageAction implements ServiceLevelAction {

	private static final String DEFAULT_RESOURCECLASS = "org.sakaiproject.localization.util.TypeProperties";
	private static final String DEFAULT_RESOURCEBUNDLE = "org.sakaiproject.localization.bundle.type.types";
	private static final String RESOURCECLASS = "resource.class.type";
	private static final String RESOURCEBUNDLE = "resource.bundle.type";
	private String resourceClass = ServerConfigurationService.getString(RESOURCECLASS, DEFAULT_RESOURCECLASS);
	private String resourceBundle = ServerConfigurationService.getString(RESOURCEBUNDLE, DEFAULT_RESOURCEBUNDLE);
	private ResourceLoader rb = new Resource().getLoader(resourceClass, resourceBundle);
	
	private String typeId;
	private String helperId;
	
	public MakeSitePageAction(String typeId, String helperId) {
		this.typeId = typeId;
		this.helperId = helperId;
	}
	
	public void initializeAction(Reference reference) {
	}

	public boolean available(ContentEntity entity) {
		return !ContentHostingService.isInDropbox(entity.getId());
	}

	public ActionType getActionType() {
		return ResourceToolAction.ActionType.MAKE_SITE_PAGE;
	}

	public String getId() {
		return ResourceToolAction.MAKE_SITE_PAGE;
	}

	public String getLabel() {
		return rb.getString("action.makesitepage"); 
	}

	public String getTypeId() {
		return this.typeId;
	}

	public void cancelAction(Reference reference) {
		// TODO Auto-generated method stub
		
	}

	public void finalizeAction(Reference reference) {
		// TODO Auto-generated method stub
		
	}

	public boolean isMultipleItemAction() {
		return false;
	}
	
}