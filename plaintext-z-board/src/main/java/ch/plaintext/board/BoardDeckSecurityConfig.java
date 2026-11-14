/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Eigene {@link SecurityFilterChain} ausschliesslich fuer die Deck-kompatible REST-API
 * ({@code /api/deck/v1/**}, Karte 2193): Die Skripte und Agenten authentifizieren sich pro
 * Request per Bearer API-Token (Validierung im {@link BoardDeckController}) — ohne Session.
 * Greift der anwendungsweite Login-Schutz dazwischen, landen die Aufrufe auf {@code 302}/Login.
 * <p>
 * Laeuft mit {@code @Order(3)} – CalDAV belegt {@code @Order(1)}, CardDAV {@code @Order(2)}.
 */
@Configuration
public class BoardDeckSecurityConfig {

    @Bean
    @Order(3)
    public SecurityFilterChain boardDeckSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/deck/v1/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // NOSONAR (S4502): kein Session-Cookie im Spiel — die API lebt vom Bearer-Token
                // pro Request; CSRF schuetzt das automatische Mitsenden von Cookies, und es gibt
                // keinen Cookie. CSRF auf diesen Pfaden zu verlangen wuerde die Skripte brechen.
                .csrf(csrf -> csrf.disable()) // NOSONAR
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable());
        return http.build();
    }
}
