/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2005, 2006, 2008 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.util;

import java.util.Iterator;
import java.util.Set;
import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.tool.cover.ActiveToolManager;

/**
 * <p>
 * Webapp listener to detect webapp-housed tool registration.
 * </p>
 * This listener can be added to the web.xml file with a snippet like this:
 * <p>
 * <code>
 * &lt;listener&gt;<br/>
 * &nbsp;&lt;listener-class&gt;org.sakaiproject.util.ToolListener&lt;/listener-class&gt;<br/>
 * &lt;/listener&gt;<br/>
 * </code>
 * </p>
 */
public class ToolListener implements ServletContextListener
{
	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(ToolListener.class);

	/**
	 * The content parameter in your web.xml specifiying the webapp root relative
	 * path to look in for the tool registration files.
	 */
	public final static String PATH = ToolListener.class.getName()+".PATH";
	
	/**
	 * Initialize.
	 */
	public void contextInitialized(ServletContextEvent event)
	{
		ServletContext context = event.getServletContext();
		String toolFolder = getToolsFolder(context);
		Set<String> paths = context.getResourcePaths(toolFolder);
		if (paths == null)
		{
			// Warn if the listener is setup but no tools found.
			M_log.warn("No tools folder found: "+ context.getRealPath(toolFolder));
			return;
		}
		int registered = 0;
		for(Iterator<String> i = paths.iterator(); i.hasNext();)
		{
			String path = i.next();

			// skip directories
			if (path.endsWith("/")) continue;

			// load this
			M_log.info("registering tools from resource: " + path);
			ActiveToolManager.register(new File(event.getServletContext().getRealPath(path)), event.getServletContext());
			registered++;
		}
		if (registered == 0)
		{
			// Probably misconfigured as we should have at least one registered.
			M_log.warn("No tools found to be registered.");
		}
	}

	/**
	 * Destroy.
	 */
	public void contextDestroyed(ServletContextEvent event)
	{
	}
	
	protected String getToolsFolder(ServletContext context)
	{
		String path = context.getInitParameter(PATH);
		if (path == null)
		{
			path = "/tools/";
		}
		else
		{
			if (!path.startsWith("/"))
			{
				path = "/"+ path;
			}
			if (!path.endsWith("/"))
			{
				path = path+ "/";
			}
		}
		return path;
	}
}
