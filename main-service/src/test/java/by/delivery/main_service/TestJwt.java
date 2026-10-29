package by.delivery.main_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

final class TestJwt {

    static final String ADMIN_USER_ID = "admin-1";

    private TestJwt() {
    }

    static RequestPostProcessor customer(String userId) {
        return token(userId, "ROLE_CUSTOMER");
    }

    static RequestPostProcessor courier(String userId) {
        return token(userId, "ROLE_COURIER");
    }

    static RequestPostProcessor admin() {
        return token(ADMIN_USER_ID, "ROLE_ADMIN");
    }

    private static RequestPostProcessor token(String userId, String role) {
        return jwt()
                .jwt(token -> token.subject(userId))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
