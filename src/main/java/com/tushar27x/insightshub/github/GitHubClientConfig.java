package com.tushar27x.insightshub.github;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class GitHubClientConfig {

    static final String GITHUB_API_URL = "https://api.github.com";

    /**
     * Shared, token-less client: every user syncs with their own token, so the
     * Authorization header is set per request, not here.
     */
    @Bean
    RestClient gitHubRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(GITHUB_API_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "insightshub") // required by GitHub's API
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
