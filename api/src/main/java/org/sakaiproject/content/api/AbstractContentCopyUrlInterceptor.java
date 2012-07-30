package org.sakaiproject.content.api;

/**
 * Self registering {@link ContentCopyUrlInterceptor}
 */
public abstract class AbstractContentCopyUrlInterceptor implements ContentCopyUrlInterceptor {
    private ContentCopyInterceptorRegistry registry;

    public void init() {
        registry.registerUrlInterceptor(this);
    }

    public void setRegistry(ContentCopyInterceptorRegistry registry) {
        this.registry = registry;
    }
}
