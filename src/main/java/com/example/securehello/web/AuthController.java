package com.example.securehello.web;

import com.example.securehello.web.dto.ErrorResponse;
import com.example.securehello.web.dto.LoginRequest;
import com.example.securehello.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Username/password login for the JSON API. On success the authenticated
 * {@link SecurityContext} is saved into the HTTP session, and the container
 * returns a {@code JSESSIONID} cookie that authenticates subsequent calls.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    /** Rotates the session id on login, preventing session fixation. */
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
            new ChangeSessionIdAuthenticationStrategy();

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping(path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username(), request.password()));

            // If a session already exists (e.g. one planted by an attacker), rotate its id.
            sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);

            return ResponseEntity.ok(LoginResponse.authenticated(authentication.getName()));
        } catch (AuthenticationException ex) {
            // Wrong password, unknown user and disabled account all look the same to the caller.
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "Unauthorized", "Invalid username or password."));
        }
    }
}
