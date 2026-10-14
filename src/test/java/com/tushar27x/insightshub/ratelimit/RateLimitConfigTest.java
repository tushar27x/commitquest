package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitConfigTest {

    static class Endpoints {
        @RateLimit(policy = "login", key = KeyType.IP)
        public void login() {
        }

        @RateLimit(policy = "logn", key = KeyType.IP) // typo
        public void typo() {
        }

        public void unlimited() {
        }
    }

    private static HandlerMethod method(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Endpoints(), name);
    }

    private static RateLimitProperties withPolicies(String... names) {
        Policy policy = new Policy(5, Duration.ofMinutes(1));
        Map<String, Policy> policies = new HashMap<>();
        for (String name : names) {
            policies.put(name, policy);
        }
        return new RateLimitProperties(true, policies);
    }

    @Test
    void definedPoliciesPass() throws Exception {
        assertThatCode(() -> RateLimitConfig.checkPolicies(
                List.of(method("login"), method("unlimited")), withPolicies("login"))).doesNotThrowAnyException();
    }

    @Test
    void undefinedPolicyFailsStartupWithItsName() throws Exception {
        assertThatThrownBy(() -> RateLimitConfig.checkPolicies(
                List.of(method("login"), method("typo")), withPolicies("login")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[logn]");
    }

    @Test
    void unknownPolicyAtRuntimeNamesIt() {
        assertThatThrownBy(() -> withPolicies("login").policy("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'missing'");
    }
}
