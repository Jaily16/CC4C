package com.cc4c.identity;

import com.cc4c.identity.api.CurrentActor;
import com.cc4c.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
/** AuthenticationController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class AuthenticationController {
    private final CurrentActor currentActor;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    AuthenticationController(CurrentActor currentActor, CookieCsrfTokenRepository csrfTokenRepository) {
        this.currentActor = currentActor;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken =
                csrfTokenRepository.loadDeferredToken(request, response).get();
        return ApiResponse.success(
                new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken()));
    }

    @GetMapping("/auth/session")
    public ApiResponse<AuthSessionResponse> session() {
        return ApiResponse.success(currentActor
                .current()
                .map(actor -> new AuthSessionResponse(true, actor.role().name(), actor.id(), actor.displayName()))
                .orElseGet(() -> new AuthSessionResponse(false, null, null, null)));
    }

    /** CsrfResponse 是不可变的数据载体，保持现有字段语义和序列化契约。 */
    public record CsrfResponse(String headerName, String parameterName, String token) {}

    /** AuthSessionResponse 是不可变的数据载体，保持现有字段语义和序列化契约。 */
    public record AuthSessionResponse(boolean authenticated, String role, String actorId, String displayName) {}
}
