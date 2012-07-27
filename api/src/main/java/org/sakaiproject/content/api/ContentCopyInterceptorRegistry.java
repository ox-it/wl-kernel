package org.sakaiproject.content.api;

/**
 * Registry service for interceptors used during a site copy.
 *
 * @author Colin Hebert
 */
public interface ContentCopyInterceptorRegistry {
    /**
     * Add a new {@link ContentCopyUrlInterceptor}
     *
     * @param copyUrlInterceptor Interceptor to register
     */
    void registerUrlInterceptor(ContentCopyUrlInterceptor copyUrlInterceptor);

    /**
     * Find the first available {@link ContentCopyUrlInterceptor} for a given url
     *
     * @param url for which an interceptor is searched
     * @return an urlInterceptor for the given URL, null if there is no interceptor matching the url
     */
    ContentCopyUrlInterceptor getUrlInterceptor(String url);
}
