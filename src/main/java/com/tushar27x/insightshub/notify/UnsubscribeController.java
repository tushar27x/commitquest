package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.OptionalLong;

/**
 * The digest's signed unsubscribe link (Phase 12). Public, answered with small HTML pages rather than
 * problem+json, because a person opens it in a browser from their mailbox.
 * <ul>
 *   <li>{@code GET} changes nothing and shows a confirm button: mail scanners prefetch links, and a GET that
 *       unsubscribed would turn alerts off for people who never clicked.</li>
 *   <li>{@code POST} turns alerts off. It's the button's target and the RFC 8058 one-click target
 *       ({@code List-Unsubscribe-Post}; the {@code List-Unsubscribe=One-Click} body is ignored). The signed token
 *       is the authorization, so it needs no login, cookie or CSRF token. Idempotent.</li>
 *   <li>A missing, forged or expired token is a 400 page. Rate-limited per IP ({@code unsubscribe}).</li>
 * </ul>
 */
@Tag(name = "Notifications", description = "The digest email's unsubscribe link. Public, answered with HTML pages: "
        + "the signed token in the link is the authorization (no JWT, cookie or CSRF token).")
@SecurityRequirements // public: overrides the API-wide bearer requirement (B9 docs)
@Slf4j
@Controller
@RequestMapping(DigestService.UNSUBSCRIBE_PATH)
public class UnsubscribeController {

    static final String CONFIRM = "unsubscribe/confirm";
    static final String DONE = "unsubscribe/done";
    static final String INVALID = "unsubscribe/invalid";

    private final UnsubscribeTokens tokens;
    private final UsersRepository usersRepository;
    private final String settingsUrl;

    public UnsubscribeController(UnsubscribeTokens tokens, UsersRepository usersRepository,
                                 InsightsHubProperties properties) {
        this.tokens = tokens;
        this.usersRepository = usersRepository;
        this.settingsUrl = properties.frontendUrl().replaceFirst("/$", "") + "/settings";
    }

    @Operation(summary = "The unsubscribe confirm page",
            description = "Changes nothing (mail scanners prefetch links): shows a button that POSTs the token back.")
    @ApiResponse(responseCode = "200", description = "The confirm page",
            content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "A missing, forged or expired token, or a login JWT",
            content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    @RateLimit(policy = "unsubscribe", key = KeyType.IP)
    @GetMapping
    public ModelAndView confirm(@Parameter(description = "The signed token from the email")
                                @RequestParam(required = false) String token) {
        if (tokens.verify(token).isEmpty()) {
            return invalid();
        }
        return new ModelAndView(CONFIRM, Map.of("token", token, "settingsUrl", settingsUrl), HttpStatus.OK);
    }

    @Operation(summary = "Turn digest emails off",
            description = "Sets alerts_enabled to false. Idempotent, and the same page for an account deleted since. "
                    + "Also the RFC 8058 one-click target: any form body is accepted (List-Unsubscribe=One-Click).")
    @ApiResponse(responseCode = "200", description = "\"Digest emails are off\"",
            content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "A missing, forged or expired token, or a login JWT",
            content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    @RateLimit(policy = "unsubscribe", key = KeyType.IP)
    @PostMapping
    public ModelAndView unsubscribe(@Parameter(description = "The signed token from the email")
                                    @RequestParam(required = false) String token) {
        OptionalLong userId = tokens.verify(token);
        if (userId.isEmpty()) {
            return invalid();
        }
        // An account deleted since the email went out gets the same page: nothing to tell apart
        if (usersRepository.disableAlerts(userId.getAsLong()) == 1) {
            log.info("Digest emails turned off for user {} by the unsubscribe link", userId.getAsLong());
        }
        return new ModelAndView(DONE, Map.of("settingsUrl", settingsUrl), HttpStatus.OK);
    }

    private ModelAndView invalid() {
        return new ModelAndView(INVALID, Map.of("settingsUrl", settingsUrl), HttpStatus.BAD_REQUEST);
    }
}
