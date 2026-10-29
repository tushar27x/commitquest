package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.config.ClockConfig;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.ratelimit.InMemoryRateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimitConfig;
import com.tushar27x.insightshub.ratelimit.RateLimitInterceptor;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET/POST /api/notifications/unsubscribe} behind the real security chain and rate limiter: public, no
 * cookie, JWT or CSRF token; HTML pages, not problem+json; only a POST changes anything.
 */
@WebMvcTest(controllers = UnsubscribeController.class, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        "insightshub.rate-limit.policies.unsubscribe.requests=3",
        "insightshub.rate-limit.policies.unsubscribe.per=1m",
})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, UnsubscribeTokens.class,
        RateLimitInterceptor.class, RateLimitConfig.class, UnsubscribeApiTest.TestBeans.class})
class UnsubscribeApiTest {

    private static final long ID = 583231L;
    private static final String PATH = "/api/notifications/unsubscribe";

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
    private UnsubscribeTokens tokens;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private UsersRepository usersRepository;

    @Test
    void getShowsAConfirmButtonAndChangesNothing() throws Exception {
        String token = tokens.issue(ID);

        mvc.perform(get(PATH).param("token", token).with(fromIp("10.0.0.1")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<form method=\"post\" action=\"/api/notifications/unsubscribe?token=" + token + "\">")))
                .andExpect(content().string(containsString("href=\"http://localhost:3000/settings\"")));

        verifyNoInteractions(usersRepository);
    }

    @Test
    void postTurnsAlertsOffWithNoCookieOrLogin() throws Exception {
        when(usersRepository.disableAlerts(ID)).thenReturn(1);

        mvc.perform(post(PATH).param("token", tokens.issue(ID)).with(fromIp("10.0.0.2")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Digest emails are off")));

        verify(usersRepository).disableAlerts(ID);
    }

    @Test
    void theOneClickPostWorks() throws Exception {
        // RFC 8058: the mail client POSTs the List-Unsubscribe URL with this form body
        mvc.perform(post(PATH + "?token=" + tokens.issue(ID)).with(fromIp("10.0.0.3"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk());

        verify(usersRepository).disableAlerts(ID);
    }

    @Test
    void aSecondPostOrAGoneAccountGetsTheSamePage() throws Exception {
        when(usersRepository.disableAlerts(ID)).thenReturn(0);

        mvc.perform(post(PATH).param("token", tokens.issue(ID)).with(fromIp("10.0.0.4")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Digest emails are off")));
    }

    @Test
    void aBadTokenIsAnHtml400() throws Exception {
        List<MockHttpServletRequestBuilder> requests =
                List.of(get(PATH), get(PATH).param("token", "nope"), post(PATH), post(PATH).param("token", "nope"));
        for (int i = 0; i < requests.size(); i++) {
            mvc.perform(requests.get(i).with(fromIp("10.0.5." + i)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("This link does not work")))
                    .andExpect(content().string(not(containsString("<form"))));
        }
        verify(usersRepository, never()).disableAlerts(anyLong());
    }

    @Test
    void aLoginTokenCantUnsubscribe() throws Exception {
        String login = jwtService.issue(ID, "octocat").value();

        mvc.perform(post(PATH).param("token", login).with(fromIp("10.0.0.6")))
                .andExpect(status().isBadRequest());

        verify(usersRepository, never()).disableAlerts(anyLong());
    }

    @Test
    void anUnsubscribeTokenCantLogIn() throws Exception {
        mvc.perform(get("/api/user/hero").header("Authorization", "Bearer " + tokens.issue(ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitedPerIp() throws Exception {
        String token = tokens.issue(ID);
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(PATH).param("token", token).with(fromIp("10.0.0.7"))).andExpect(status().isOk());
        }
        mvc.perform(post(PATH).param("token", token).with(fromIp("10.0.0.7")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mvc.perform(get(PATH).param("token", token).with(fromIp("10.0.0.8"))).andExpect(status().isOk());
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
