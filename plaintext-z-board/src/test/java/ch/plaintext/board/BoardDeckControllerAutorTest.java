/*
 * Copyright (C) plaintext.ch, 2026.
 */
package ch.plaintext.board;

import ch.plaintext.apitoken.IApiTokenService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Karte 2342: Agenten-Tokens laufen unter Daniels Benutzer. Ein Kommentar ueber ein {@code agent-*}-Token
 * traegt den Tokennamen als Autor, jedes andere Token weiter die E-Mail des Benutzers.
 */
class BoardDeckControllerAutorTest {

    private static IApiTokenService.ApiTokenValidationResult token(String name) {
        return new IApiTokenService.ApiTokenValidationResult(1L, "plaintext", "daniel@example.ch", name,
                Instant.now().plusSeconds(3600), "WRITE");
    }

    @Test
    void agentTokenSchreibtUnterSeinemNamen() {
        assertEquals("agent-kimi1", BoardDeckController.autor(token("agent-kimi1")));
    }

    @Test
    void anderesTokenBehaeltDieEmail() {
        assertEquals("daniel@example.ch", BoardDeckController.autor(token("Bosrd")));
        assertEquals("daniel@example.ch", BoardDeckController.autor(token(null)));
    }
}
