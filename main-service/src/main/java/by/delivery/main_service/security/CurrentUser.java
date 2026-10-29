package by.delivery.main_service.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// Достаёт данные текущего пользователя из JWT, который уже проверил Spring Security.
// Идентификатор берётся только из токена, поэтому подделать его запросом нельзя.
@Component
public class CurrentUser {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    // sub — уникальный id пользователя в Keycloak (UUID), он же userId в таблицах clients, couriers, notifications
    public String id() {
        String subject = jwt().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AccessDeniedException("В токене нет идентификатора пользователя (sub)");
        }
        return subject;
    }

    public boolean isAdmin() {
        return authentication().getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    private Jwt jwt() {
        if (authentication().getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("Запрос без JWT-токена");
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Пользователь не аутентифицирован");
        }
        return authentication;
    }
}
