package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.config.ProdProfile;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B10: the client IP the IP-keyed rate limits ({@code login}, {@code callback}, {@code unsubscribe})
 * see in production. The {@code prod} profile's settings go through Boot's own Tomcat customizer, and
 * the resulting {@link RemoteIpValve} handles a request the way Tomcat would before Spring sees it.
 * <p>
 * Caddy reaches the backend from the compose network (a private address), so its
 * {@code X-Forwarded-For} is used; the same header from a public address is a client's own and ignored.
 */
class ForwardedClientIpTest {

    private static final String CADDY = "172.18.0.2";       // compose network, inside 172.16.0.0/12
    private static final String PUBLIC_CLIENT = "203.0.113.7"; // TEST-NET-3: no proxy of ours
    private static final String VISITOR = "198.51.100.23";     // TEST-NET-2: the address Caddy saw

    static class Endpoints {
        @RateLimit(policy = "login", key = KeyType.IP)
        public void login() {
        }
    }

    @Test
    void prodProfileInstallsTheRemoteIpValve() {
        assertThat(remoteIpValve(ProdProfile.environment())).isNotNull();
    }

    @Test
    void forwardedHeaderFromCaddyOnThePrivateNetworkIsUsed() throws Exception {
        assertThat(rateLimitKey(CADDY, VISITOR)).isEqualTo("login:ip:" + VISITOR);
    }

    @Test
    void forwardedHeaderFromLoopbackIsUsed() throws Exception {
        assertThat(rateLimitKey("127.0.0.1", VISITOR)).isEqualTo("login:ip:" + VISITOR);
    }

    @Test
    void forwardedHeaderFromAPublicAddressIsIgnored() throws Exception {
        // a client talking to Tomcat directly, trying to look like someone else
        assertThat(rateLimitKey(PUBLIC_CLIENT, VISITOR)).isEqualTo("login:ip:" + PUBLIC_CLIENT);
    }

    @Test
    void spoofedEntriesBeforeCaddysAreIgnored() throws Exception {
        // Caddy replaces a visitor's X-Forwarded-For; if a proxy ever appended instead, the valve still
        // takes the right-most address that isn't internal: the one our proxy saw, not the claimed one.
        assertThat(rateLimitKey(CADDY, PUBLIC_CLIENT + ", " + VISITOR)).isEqualTo("login:ip:" + VISITOR);
    }

    @Test
    void withoutTheProfileForwardedHeadersAreNeverTrusted() {
        assertThat(remoteIpValve(ProdProfile.environmentWithoutProfile())).isNull();
    }

    private static String rateLimitKey(String remoteAddr, String forwardedFor) throws Exception {
        RemoteIpValve valve = remoteIpValve(ProdProfile.environment());
        AtomicReference<String> key = new AtomicReference<>();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                RateLimit limit;
                try {
                    limit = Endpoints.class.getMethod("login").getAnnotation(RateLimit.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
                SecurityContextHolder.clearContext();
                key.set(RateLimitInterceptor.key(limit, request));
            }
        });

        Request request = new Request(new Connector(), new org.apache.coyote.Request());
        request.setRemoteAddr(remoteAddr);
        request.getCoyoteRequest().getMimeHeaders().addValue("X-Forwarded-For").setString(forwardedFor);
        request.getCoyoteRequest().getMimeHeaders().addValue("X-Forwarded-Proto").setString("https");
        valve.invoke(request, null);
        return key.get();
    }

    private static RemoteIpValve remoteIpValve(StandardEnvironment environment) {
        Binder binder = Binder.get(environment);
        ServerProperties server = binder.bindOrCreate("server", ServerProperties.class);
        TomcatServerProperties tomcat = binder.bindOrCreate("server.tomcat", TomcatServerProperties.class);
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new TomcatWebServerFactoryCustomizer(environment, server, tomcat, new WebProperties()).customize(factory);
        List<Valve> valves = List.copyOf(factory.getEngineValves());
        return valves.stream()
                .filter(RemoteIpValve.class::isInstance)
                .map(RemoteIpValve.class::cast)
                .findFirst()
                .orElse(null);
    }
}
