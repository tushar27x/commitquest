package com.tushar27x.insightshub.config;

import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.tushar27x.insightshub.error.ApiProblem;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

/**
 * B9: the OpenAPI document ({@code /v3/api-docs}) and Swagger UI ({@code /swagger-ui.html}), on only while
 * {@code springdoc.api-docs.enabled} ({@code API_DOCS_ENABLED}) is true. {@code docs/api/contract.md} stays the
 * source of truth; this describes the same endpoints from the code.
 * <ul>
 *   <li>Every endpoint needs the bearer JWT unless it carries an empty {@link SecurityRequirements} (the login
 *       round trip and the unsubscribe pages).</li>
 *   <li>The errors every endpoint shares are added here, not on each method: 401 on the JWT endpoints, 429 with
 *       {@code Retry-After} and the {@code X-RateLimit-*} headers on the {@link RateLimit} ones, 500 everywhere.</li>
 *   <li>Property names come from the DTOs' Jackson 3 {@code @JsonNaming}, which swagger-core (Jackson 2) can't see
 *       on its own (B40): {@link #modelResolver} bridges it, so the schemas are snake_case like the API.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    public static final String BEARER = "bearer-jwt";
    static final String PROBLEM_JSON = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
    static final String PROBLEM_REF = "#/components/schemas/Problem";

    @Bean
    OpenAPI insightsHubOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The JWT from the login: HS256, 24 h, sub = GitHub id. The web app gets it "
                                + "from the #token fragment of the login redirect."));
        // the error body, registered once so every response can $ref it
        ModelConverters.getInstance(true)
                .readAll(new AnnotatedType(ApiProblem.class))
                .forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("Insight Hub API")
                        .version("v2")
                        .description("The backend of the Insight Hub hero tracker. JSON keys are snake_case, "
                                + "timestamps ISO-8601 UTC, calendar dates YYYY-MM-DD in the user's zone, enums "
                                + "UPPER_CASE. Every error is application/problem+json. The contract the web app "
                                + "builds on is docs/api/contract.md."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * swagger-core's model resolver, with a Jackson 2 mapper that also reads the Jackson 3 {@code @JsonNaming} the
     * DTOs use. springdoc puts a {@code ModelResolver} bean ahead of its default one (springdoc's documented way to
     * change how models are named).
     */
    @Bean
    ModelResolver modelResolver(ObjectMapperProvider objectMapperProvider) {
        var mapper = objectMapperProvider.jsonMapper().copy();
        mapper.setAnnotationIntrospector(new Jackson3NamingIntrospector());
        return new ModelResolver(mapper).openapi31(objectMapperProvider.isOpenapi31());
    }

    /** The shared responses: see the class comment. */
    @Bean
    OperationCustomizer sharedResponses(RateLimitProperties rateLimits) {
        return (operation, handler) -> {
            ApiResponses responses = operation.getResponses();
            responses.forEach((status, response) -> {
                if (status.startsWith("3")) {
                    response.setContent(null); // a redirect has no body, whatever the method returns
                } else if ((status.startsWith("4") || status.startsWith("5")) && !isHtml(response)) {
                    // the controllers declare an error's status and meaning; its body is always the problem
                    response.setContent(problem(response.getDescription()).getContent());
                }
            });
            if (!isPublic(handler)) {
                responses.putIfAbsent("401", problem("Missing, invalid or expired JWT: log in again. "
                        + "Carries WWW-Authenticate.").addHeaderObject(HttpHeaders.WWW_AUTHENTICATE, stringHeader(
                        "Bearer, with error=\"invalid_token\" when a token was sent")));
            }
            RateLimit limit = handler.getMethodAnnotation(RateLimit.class);
            if (limit != null) {
                addRateLimit(operation, limit, rateLimits.policies().get(limit.policy()));
            }
            responses.putIfAbsent("500", problem("Anything unexpected; the exception text is never returned. "
                    + "Treat like 502."));
            return operation;
        };
    }

    /** The unsubscribe pages answer errors with HTML, like their 200s. */
    private static boolean isHtml(ApiResponse response) {
        return response.getContent() != null
                && response.getContent().containsKey(org.springframework.http.MediaType.TEXT_HTML_VALUE);
    }

    /** Public: an empty {@code @SecurityRequirements} on the method or its class. */
    static boolean isPublic(HandlerMethod handler) {
        SecurityRequirements onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
                SecurityRequirements.class);
        SecurityRequirements requirements = onMethod != null ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), SecurityRequirements.class);
        return requirements != null && requirements.value().length == 0;
    }

    private static void addRateLimit(Operation operation, RateLimit limit, RateLimitProperties.Policy policy) {
        String rule = "policy `" + limit.policy() + "`"
                + (policy == null ? "" : ", " + policy.requests() + " per " + policy.per().toSeconds() + " s")
                + (limit.key() == RateLimit.KeyType.IP ? " per IP" : " per user");
        ApiResponses responses = operation.getResponses();
        ApiResponse tooMany = responses.get("429");
        if (tooMany == null) {
            tooMany = problem("Rate limited (" + rule + "): wait Retry-After seconds.");
            responses.addApiResponse("429", tooMany);
        } else {
            tooMany.setDescription(tooMany.getDescription() + " Also the rate limit (" + rule + ").");
        }
        tooMany.addHeaderObject(HttpHeaders.RETRY_AFTER, integerHeader("Whole seconds to wait, at least 1"));
        // the interceptor sets both headers on every response it lets through (redirects too), and on its 429
        responses.forEach((status, response) -> {
            if (status.startsWith("2") || status.startsWith("3") || status.equals("429")) {
                response.addHeaderObject("X-RateLimit-Limit", integerHeader("Requests allowed per window"));
                response.addHeaderObject("X-RateLimit-Remaining", integerHeader("Requests left in this window"));
            }
        });
    }

    static ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
    }

    private static Header integerHeader(String description) {
        return new Header().description(description).schema(new IntegerSchema());
    }

    private static Header stringHeader(String description) {
        return new Header().description(description).schema(new Schema<String>().type("string"));
    }

    /**
     * Jackson 2's introspector, plus the Jackson 3 {@code tools.jackson.databind.annotation.JsonNaming}. The other
     * annotations the DTOs use ({@code @JsonProperty}, {@code @JsonValue}) live in {@code com.fasterxml.jackson.annotation},
     * which both versions share.
     */
    static final class Jackson3NamingIntrospector extends JacksonAnnotationIntrospector {

        private static final Map<Class<?>, com.fasterxml.jackson.databind.PropertyNamingStrategy> STRATEGIES = Map.of(
                tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class,
                com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE,
                tools.jackson.databind.PropertyNamingStrategies.UpperSnakeCaseStrategy.class,
                com.fasterxml.jackson.databind.PropertyNamingStrategies.UPPER_SNAKE_CASE,
                tools.jackson.databind.PropertyNamingStrategies.LowerCamelCaseStrategy.class,
                com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE,
                tools.jackson.databind.PropertyNamingStrategies.UpperCamelCaseStrategy.class,
                com.fasterxml.jackson.databind.PropertyNamingStrategies.UPPER_CAMEL_CASE,
                tools.jackson.databind.PropertyNamingStrategies.KebabCaseStrategy.class,
                com.fasterxml.jackson.databind.PropertyNamingStrategies.KEBAB_CASE,
                tools.jackson.databind.PropertyNamingStrategies.LowerCaseStrategy.class,
                com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CASE);

        @Override
        public Object findNamingStrategy(AnnotatedClass ac) {
            tools.jackson.databind.annotation.JsonNaming naming =
                    ac.getRawType().getAnnotation(tools.jackson.databind.annotation.JsonNaming.class);
            if (naming != null) {
                var strategy = STRATEGIES.get(naming.value());
                if (strategy == null) {
                    throw new IllegalStateException("No Jackson 2 equivalent of " + naming.value().getName()
                            + " on " + ac.getRawType().getName() + "; add it to the map");
                }
                return strategy;
            }
            return super.findNamingStrategy(ac);
        }
    }
}
