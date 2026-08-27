package com.campus.secondhand.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campus.secondhand.security.CurrentActor;
import com.campus.secondhand.security.CurrentActorService;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentActorServiceTest {
    private UserRepository users;
    private HttpServletRequest request;
    private HttpSession session;
    private CurrentActorService actors;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        actors = new CurrentActorService(users, request);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingOrAnonymousAuthentication() {
        assertThrows(AccessDeniedException.class, actors::require);

        Authentication anonymous = mock(Authentication.class);
        when(anonymous.isAuthenticated()).thenReturn(true);
        when(anonymous.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThrows(AccessDeniedException.class, actors::require);
    }

    @Test
    void rejectsUnknownUserAndInactiveUser() {
        authenticateAs("missing@example.com");
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, actors::require);

        User disabled = user(7L, "disabled@example.com", "STUDENT", "DISABLED", 3);
        authenticateAs(disabled.getEmail());
        when(users.findByEmailIgnoreCase(disabled.getEmail())).thenReturn(Optional.of(disabled));
        assertThrows(AccessDeniedException.class, actors::require);
    }

    @Test
    void rejectsMissingOrStaleSessionAuthVersion() {
        User student = user(8L, "student@example.com", "STUDENT", "ACTIVE", 4);
        authenticateAs(student.getEmail());
        when(users.findByEmailIgnoreCase(student.getEmail())).thenReturn(Optional.of(student));

        when(request.getSession(false)).thenReturn(null);
        assertThrows(AccessDeniedException.class, actors::require);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_VERSION")).thenReturn(3);
        assertThrows(AccessDeniedException.class, actors::require);
    }

    @Test
    void returnsStudentActorOnlyWhenSessionVersionMatches() {
        User student = user(9L, "student@example.com", "STUDENT", "ACTIVE", 4);
        authenticateAs(student.getEmail());
        when(users.findByEmailIgnoreCase(student.getEmail())).thenReturn(Optional.of(student));
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_VERSION")).thenReturn(4);

        CurrentActor actor = actors.require();

        assertNotNull(actor);
        assertEquals(student.getId(), actor.userId());
        assertEquals("STUDENT", actor.role());
        assertFalse(actor.isAdmin());
    }

    @Test
    void preservesAdminRoleOnlyForActiveMatchingSession() {
        User admin = user(10L, "admin@example.com", "ADMIN", "ACTIVE", 2);
        authenticateAs(admin.getEmail());
        when(users.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("AUTH_VERSION")).thenReturn(2);

        CurrentActor actor = actors.require();

        assertEquals(10L, actor.userId());
        assertEquals("ADMIN", actor.role());
        assertTrue(actor.isAdmin());
    }

    @Test
    void acceptsGatewayJwtWithoutReadingTheMonolithUserTable() {
        Jwt jwt = Jwt.withTokenValue("internal-token")
            .header("alg", "HS256")
            .subject("42")
            .claim("role", "STUDENT")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        CurrentActor actor = actors.require();

        assertEquals(42L, actor.userId());
        assertEquals("STUDENT", actor.role());
    }

    private void authenticateAs(String email) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            email, "n/a", java.util.List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static User user(long id, String email, String role, String status, int authVersion) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(status);
        user.setAuthVersion(authVersion);
        return user;
    }
}
