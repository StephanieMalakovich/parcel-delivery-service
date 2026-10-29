package by.delivery.main_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

final class TestJwt {

    static final int ADMIN_USER_ID = 1;

    private TestJwt() {
    }

    static RequestPostProcessor customer(int userId) {
        return token(userId, "ROLE_CUSTOMER");
    }

    static RequestPostProcessor courier(int userId) {
        return token(userId, "ROLE_COURIER");
    }

    static RequestPostProcessor admin() {
        return token(ADMIN_USER_ID, "ROLE_ADMIN");
    }

    private static RequestPostProcessor token(int userId, String role) {
        return jwt()
                .jwt(token -> token.claim("user_id", userId))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
