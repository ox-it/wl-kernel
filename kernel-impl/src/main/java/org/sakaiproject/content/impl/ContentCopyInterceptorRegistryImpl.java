package org.sakaiproject.content.impl;

import org.sakaiproject.content.api.ContentCopyInterceptorRegistry;
import org.sakaiproject.content.api.ContentCopyUrlInterceptor;

import java.util.Collection;
import java.util.LinkedList;

/**
 * @author Colin Hebert
 */
public class ContentCopyInterceptorRegistryImpl implements ContentCopyInterceptorRegistry {
    private final Collection<ContentCopyUrlInterceptor> urlInterceptors = new LinkedList<ContentCopyUrlInterceptor>();
    public void registerUrlInterceptor(ContentCopyUrlInterceptor copyUrlInterceptor) {
        urlInterceptors.add(copyUrlInterceptor);
    }

    public ContentCopyUrlInterceptor getUrlInterceptor(String url) {
        for(ContentCopyUrlInterceptor urlInterceptor : urlInterceptors){
            if(urlInterceptor.isUrlHandled(url))
                return urlInterceptor;
        }

        return null;
    }
}
