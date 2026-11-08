package dev.likezendesk.identity.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class IdentityWebConfig implements WebMvcConfigurer {

    private final ViewerArgumentResolver viewerArgumentResolver;

    IdentityWebConfig(ViewerArgumentResolver viewerArgumentResolver) {
        this.viewerArgumentResolver = viewerArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(viewerArgumentResolver);
    }
}
