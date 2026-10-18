package com.tushar27x.insightshub.auth;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** The web app's hand-off (B5): the JWT goes in the fragment, which browsers never send to a server. */
class AuthRedirectTest {

    @Test
    void theTokenIsAFragmentNotAQueryParameter() {
        String jwt = String.join(".", "header", "payload", "signature"); // shape only, built at runtime (B31)

        URI dashboard = AuthController.dashboardUri("http://localhost:3000", jwt);

        assertThat(dashboard.toString()).isEqualTo("http://localhost:3000/dashboard#token=" + jwt);
        assertThat(dashboard.getQuery()).isNull();
        assertThat(dashboard.getFragment()).isEqualTo("token=" + jwt);
    }
}
