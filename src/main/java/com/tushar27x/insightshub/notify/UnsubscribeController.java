package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
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

    @RateLimit(policy = "unsubscribe", key = KeyType.IP)
    @GetMapping
    public ModelAndView confirm(@RequestParam(required = false) String token) {
        if (tokens.verify(token).isEmpty()) {
            return invalid();
        }
        return new ModelAndView(CONFIRM, Map.of("token", token, "settingsUrl", settingsUrl), HttpStatus.OK);
    }

    @RateLimit(policy = "unsubscribe", key = KeyType.IP)
    @PostMapping
    public ModelAndView unsubscribe(@RequestParam(required = false) String token) {
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
