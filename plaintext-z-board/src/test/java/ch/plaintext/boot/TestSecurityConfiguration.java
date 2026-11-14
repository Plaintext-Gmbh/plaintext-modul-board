package ch.plaintext.boot;

import ch.plaintext.PlaintextSecurity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;

/**
 * Mock security for integration tests: user 1 / mandat TEST.
 */
@TestConfiguration
public class TestSecurityConfiguration {

    @Bean
    @Primary
    public PlaintextSecurity plaintextSecurity() {
        return new PlaintextSecurity() {
            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public String getMandat() {
                return "TEST";
            }

            @Override
            public Set<String> getAllMandate() {
                return Set.of();
            }

            @Override
            public String getUser() {
                return "testuser";
            }

            @Override
            public Authentication getAuthentication() {
                return null;
            }

            @Override
            public String getMandatForUser(long userId) {
                return "TEST";
            }

            @Override
            public String getUsernameForUser(long userId) {
                return "testuser";
            }

            @Override
            public boolean ifGranted(String role) {
                return false;
            }

            @Override
            public List<String> getUsersForMandat(String mandat) {
                return List.of();
            }

            @Override
            public String getStartpageOrDefault() {
                return "/index.html?faces-redirect=true";
            }

            @Override
            public boolean isImpersonating() {
                return false;
            }

            @Override
            public void startImpersonation(Long userId) {
                // No-op for tests
            }

            @Override
            public void stopImpersonation() {
                // No-op for tests
            }

            @Override
            public Long getOriginalUserId() {
                return null;
            }
        };
    }
}
