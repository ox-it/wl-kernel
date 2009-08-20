package org.sakaiproject.util;

import java.io.IOException;
import java.net.URLEncoder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.tool.cover.SessionManager;

/**
 * Filter which looks for a request that could be logged in but isn't and
 * redirects, preserving the original URL. Must go after the sakai 
 * request filter.
 * 
 * @author buckett
 * 
 */
public class OptimisticLogin implements Filter {

	private static final Log log = LogFactory.getLog(OptimisticLogin.class);

	private String cookieName = "webauth_found";

	private String loginUrl = "/sakai-login-tool/container";

	private String redirectParam = "redirect";

	public void destroy() {
		log.debug("destroy");
	}

	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (request instanceof HttpServletRequest
				&& response instanceof HttpServletResponse) {
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			if (hasCookie(httpRequest) && httpRequest.getParameter(redirectParam) == null) {
				String userId = SessionManager.getCurrentSessionUserId();
				if (userId == null) {
					// Ok, time to redirect then.
					HttpServletResponse httpResponse = (HttpServletResponse) response;
					StringBuffer originalUrl = httpRequest.getRequestURL();
					String queryString = httpRequest.getQueryString();
					if (queryString != null) {
							originalUrl.append("?").append(httpRequest.getQueryString());
					}
					String redirectUrl = new StringBuilder(loginUrl).append(
							"?").append(redirectParam).append("=").append(
							URLEncoder.encode(originalUrl.toString(), "UTF-8")).toString();
					httpResponse.sendRedirect(redirectUrl);
					return;
				}
			}
		}
		chain.doFilter(request, response);
	}

	public void init(FilterConfig filterConfig) throws ServletException {
		log.debug("init");
	}

	/**
	 * See if the request has the magic cookie.
	 * 
	 * @param request
	 *            ServletRequest
	 * @return true if it does.
	 */
	private boolean hasCookie(HttpServletRequest request) {
		boolean hasCookie = false;
		boolean multipleMatches = false;
		String domain = request.getServerName();
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (cookieName.equals(cookie.getName())) {
					// If found multiple cookie.
					multipleMatches = hasCookie;
					// IE seems to send too many cookies.
					hasCookie = true;
				}
			}
			if (multipleMatches && log.isDebugEnabled()) {
				log.debug("Multiple cookies for '" + cookieName + "' found.");
			}
		}
		return hasCookie;
	}

}
