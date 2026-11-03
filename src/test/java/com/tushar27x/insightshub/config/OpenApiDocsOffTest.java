package com.tushar27x.insightshub.config;

import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.ratelimit.InMemoryRateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimitConfig;
import com.tushar27x.insightshub.ratelimit.RateLimitInterceptor;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.user.HeroClassService;
import com.tushar27x.insightshub.user.HeroController;
import com.tushar27x.insightshub.user.HeroQueryService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B9, the default ({@code API_DOCS_ENABLED} unset): springdoc on the classpath but switched off. Nothing is generated
 * and the docs' paths are like any unknown path, behind the JWT. Set explicitly here so a developer's {@code .env}
 * with the docs on doesn't change the test.
 */
@WebMvcTest(controllers = HeroController.class, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class,
        RateLimitInterceptor.class, RateLimitConfig.class, OpenApiConfig.class, OpenApiDocsOffTest.TestBeans.class})
class OpenApiDocsOffTest {

    @TestConfiguration
    @EnableConfigurationProperties({InsightsHubProperties.class, AuthProperties.class, RateLimitProperties.class})
    static class TestBeans {
        @Bean
        InMemoryRateLimiter rateLimiter() {
            return new InMemoryRateLimiter();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private HeroQueryService heroQueryService;
    @MockitoBean
    private HeroClassService heroClassService;

    @Test
    void theDocsAreNotServedAndNeedAJwt() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config", "/swagger-ui/index.html",
                "/swagger-ui.html")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void nothingIsGenerated() {
        assertThat(context.getBeanNamesForType(OpenApiConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(org.springdoc.core.service.OpenAPIService.class)).isEmpty();
    }
}
