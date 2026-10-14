package com.tushar27x.insightshub.ratelimit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;
    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitInterceptor interceptor, RateLimitProperties properties) {
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }

    /**
     * A typo in a policy name would otherwise surface as a 500 on the first request to that endpoint;
     * this makes it a startup failure instead.
     */
    @EventListener(ApplicationReadyEvent.class)
    void checkPolicies(ApplicationReadyEvent event) {
        RequestMappingHandlerMapping mapping = event.getApplicationContext()
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        checkPolicies(mapping.getHandlerMethods().values(), properties);
    }

    static void checkPolicies(Collection<HandlerMethod> methods, RateLimitProperties properties) {
        List<String> missing = methods.stream()
                .map(method -> method.getMethodAnnotation(RateLimit.class))
                .filter(Objects::nonNull)
                .map(RateLimit::policy)
                .filter(policy -> !properties.policies().containsKey(policy))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("@RateLimit uses undefined policies " + missing
                    + "; add them under insightshub.rate-limit.policies");
        }
    }
}
