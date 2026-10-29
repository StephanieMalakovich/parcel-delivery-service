package by.delivery.main_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final String userIdClaim;

    public CurrentUser(@Value("${app.security.user-id-claim:user_id}") String userIdClaim) {
        this.userIdClaim = userIdClaim;
    }

    public Integer id() {
        Object value = jwt().getClaim(userIdClaim);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
            }
        }
        throw new AccessDeniedException("В токене нет числового claim '" + userIdClaim + "'");
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
