package com.cc4c.identity;

import com.cc4c.identity.api.ActorIdentity;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController {
    private final CurrentActor currentActor;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    AuthenticationController(
            CurrentActor currentActor,
            CookieCsrfTokenRepository csrfTokenRepository) {
        this.currentActor = currentActor;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(
            HttpServletRequest request,
            HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.loadDeferredToken(request, response).get();
        return ApiResponse.success(new CsrfResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()));
    }

    @GetMapping("/auth/session")
    public ApiResponse<AuthSessionResponse> session() {
        return ApiResponse.success(currentActor.current()
                .map(actor -> new AuthSessionResponse(
                        true,
                        actor.role().name(),
                        actor.id(),
                        actor.displayName()))
                .orElseGet(() -> new AuthSessionResponse(false, null, null, null)));
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record AuthSessionResponse(
            boolean authenticated,
            String role,
            String actorId,
            String displayName) {
    }
}
