/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board.mcp;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.board.BoardDeckJson.BoardJson;
import ch.plaintext.board.BoardDeckJson.CardJson;
import ch.plaintext.board.BoardDeckJson.CommentJson;
import ch.plaintext.board.BoardDeckJson.StackJson;
import ch.plaintext.board.BoardDeckService;
import ch.plaintext.mcp.McpLiterale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * MCP-Tools fuer das Kanban-Board (Karte 2193): natuerlichsprachliche Board-Arbeit ohne REST-Aufrufe.
 * Das Mandat kommt ausschliesslich aus {@link PlaintextSecurity} — es ist kein Mandats-Parameter
 * erlaubt; das Scoping erledigt der {@link BoardDeckService}. Die Karten-Ids entsprechen nach der
 * Migration (Phase 2) den Nextcloud-Deck-Ids — Agenten und Memories verweisen unveraendert darauf.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoardMcpTools {

    private final BoardDeckService service;
    private final PlaintextSecurity security;

    // ── Tools ──────────────────────────────────────────────

    @McpTool(name = "board_list_boards",
            description = "Listet die Kanban-Boards des eigenen Mandats: id und Titel. Beim Plaintext-Backlog "
            + "ist es genau eines (das migrierte Nextcloud-Deck-Board).")
    @PreAuthorize("hasAuthority('SCOPE_READ')")
    public List<BoardJson> listBoards() {
        log.info("MCP: board_list_boards mandat={}", security.getMandat());
        return demaskiert("board_list_boards", () -> service.boards(security.getMandat()));
    }

    @McpTool(name = "board_list_stacks",
            description = "Listet die Stapel eines Boards mit ihren Karten (id, Titel, Reihenfolge) — das "
            + "Grundgeruest zum Arbeiten mit dem Board. Reihenfolge der Karten = 'order' aufsteigend. "
            + "boardId weglassen, wenn das Mandat genau ein Board hat.")
    @PreAuthorize("hasAuthority('SCOPE_READ')")
    public List<StackJson> listStacks(
            @McpToolParam(required = false,
                    description = "Board-Id (siehe board_list_boards); null/leer = das einzige Board des "
                    + "Mandats — Fehler, wenn es mehrere gibt") Long boardId) {
        log.info("MCP: board_list_stacks boardId={} mandat={}", boardId, security.getMandat());
        return demaskiert("board_list_stacks", () -> service.stacks(board(boardId), security.getMandat()));
    }

    @McpTool(name = "board_get_card",
            description = "Liefert eine Karte vollstaendig: id, Titel, Beschreibung, Stapel, Reihenfolge, "
            + "Faelligkeitsdatum, Labels und ALLE Kommentare (neueste zuletzt).")
    @PreAuthorize("hasAuthority('SCOPE_READ')")
    public CardInfo getCard(
            @McpToolParam(description = "Karten-Id (entspricht der Deck-Id)") Long cardId) {
        log.info("MCP: board_get_card cardId={} mandat={}", cardId, security.getMandat());
        return demaskiert("board_get_card", () -> {
            pflicht(cardId, "cardId");
            return new CardInfo(service.card(cardId, security.getMandat()),
                    service.comments(cardId, security.getMandat()));
        });
    }

    @McpTool(name = "board_create_card",
            description = "Legt eine neue Karte im gegebenen Stapel an (Titel Pflicht, Beschreibung "
            + "Markdown). Ohne Reihenfolge landet die Karte am Stapelende. Liefert die angelegte Karte.")
    @PreAuthorize("hasAuthority('SCOPE_WRITE')")
    public CardJson createCard(
            @McpToolParam(description = "Stapel-Id (siehe board_list_stacks)") Long stackId,
            @McpToolParam(description = "Kartentitel") String titel,
            @McpToolParam(required = false, description = "Beschreibung (Markdown), optional") String beschreibung,
            @McpToolParam(required = false,
                    description = "Position im Stapel (0 = oben); ohne Angabe ans Ende") Integer reihenfolge) {
        log.info("MCP: board_create_card stackId={} mandat={}", stackId, security.getMandat());
        return demaskiert("board_create_card", () -> {
            pflicht(stackId, "stackId");
            if (titel == null || titel.isBlank()) {
                throw new IllegalArgumentException("titel darf nicht leer sein");
            }
            return service.createCard(stackId, titel.trim(),
                    McpLiterale.ohneNullLiteral(beschreibung), reihenfolge, null, security.getMandat());
        });
    }

    @McpTool(name = "board_move_card",
            description = "Verschiebt eine Karte in einen anderen Stapel (z.B. todo -> inprogress -> done) "
            + "und/oder an eine neue Position. Ohne Reihenfolge ans Stapelende.")
    @PreAuthorize("hasAuthority('SCOPE_WRITE')")
    public String moveCard(
            @McpToolParam(description = "Karten-Id") Long cardId,
            @McpToolParam(description = "Ziel-Stapel-Id (siehe board_list_stacks)") Long zielStackId,
            @McpToolParam(required = false,
                    description = "Position im Zielstapel (0 = oben); ohne Angabe ans Ende") Integer reihenfolge) {
        log.info("MCP: board_move_card cardId={} zielStackId={} mandat={}", cardId, zielStackId,
                security.getMandat());
        return demaskiert("board_move_card", () -> {
            pflicht(cardId, "cardId");
            pflicht(zielStackId, "zielStackId");
            service.moveCard(cardId, zielStackId, reihenfolge == null ? 999 : reihenfolge, security.getMandat());
            return "Karte " + cardId + " nach Stapel " + zielStackId + " verschoben";
        });
    }

    @McpTool(name = "board_append_description",
            description = "Haengt Text an die Beschreibung einer Karte an (bestehender Text, Leerzeile, "
            + "neuer Text) — der uebliche Weg fuer Fortschritts-/Arbeitsnotizen an einer Karte. "
            + "Liefert die Karte mit der neuen Beschreibung.")
    @PreAuthorize("hasAuthority('SCOPE_WRITE')")
    public CardJson appendDescription(
            @McpToolParam(description = "Karten-Id") Long cardId,
            @McpToolParam(description = "Anzuhaengender Text") String text) {
        log.info("MCP: board_append_description cardId={} mandat={}", cardId, security.getMandat());
        return demaskiert("board_append_description", () -> {
            pflicht(cardId, "cardId");
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text darf nicht leer sein");
            }
            return service.appendDescription(cardId, text.trim(), security.getMandat());
        });
    }

    @McpTool(name = "board_add_comment",
            description = "Schreibt einen Kommentar an eine Karte — sichtbar fuer alle Board-Nutzer, "
            + "Autor ist der aktuelle Benutzer. Liefert den angelegten Kommentar.")
    @PreAuthorize("hasAuthority('SCOPE_WRITE')")
    public CommentJson addComment(
            @McpToolParam(description = "Karten-Id") Long cardId,
            @McpToolParam(description = "Kommentartext") String text) {
        log.info("MCP: board_add_comment cardId={} mandat={}", cardId, security.getMandat());
        return demaskiert("board_add_comment", () -> {
            pflicht(cardId, "cardId");
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text darf nicht leer sein");
            }
            return service.addComment(cardId, text.trim(), String.valueOf(security.getId()),
                    security.getUser(), security.getMandat());
        });
    }

    @McpTool(name = "board_set_label",
            description = "Setzt ein Label an eine Karte — nach Titel gesucht, auf dem Board angelegt, "
            + "wenn es noch fehlt (Farbe nur beim Anlegen relevant, Hex ohne '#'). Bereits gesetzte "
            + "Labels bleiben. Liefert die Karte mit allen Labels.")
    @PreAuthorize("hasAuthority('SCOPE_WRITE')")
    public CardJson setLabel(
            @McpToolParam(description = "Karten-Id") Long cardId,
            @McpToolParam(description = "Label-Titel, z.B. 'kimi' oder 'Bug'") String titel,
            @McpToolParam(required = false,
                    description = "Farbe beim Anlegen, Hex ohne '#' (Vorgabe 999999 = grau)") String farbe) {
        log.info("MCP: board_set_label cardId={} titel={} mandat={}", cardId, titel, security.getMandat());
        return demaskiert("board_set_label", () -> {
            pflicht(cardId, "cardId");
            if (titel == null || titel.isBlank()) {
                throw new IllegalArgumentException("titel darf nicht leer sein");
            }
            return service.setLabel(cardId, titel.trim(), McpLiterale.ohneNullLiteral(farbe),
                    security.getMandat());
        });
    }

    // ── intern ─────────────────────────────────────────────

    /** Ein Board explizit oder — ohne boardId — das einzige Board des Mandats. */
    private long board(Long boardId) {
        if (boardId != null) {
            return boardId;
        }
        List<BoardJson> boards = service.boards(security.getMandat());
        if (boards.size() != 1) {
            throw new IllegalArgumentException("Mandat hat " + boards.size() + " Boards — boardId angeben");
        }
        return boards.get(0).id;
    }

    private static void pflicht(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " muss eine positive Zahl sein");
        }
    }

    /**
     * Garantiert, dass eine geworfene Exception eine NICHT-NULL Message hat — das MCP-Framework baut
     * aus einer message-losen Exception (klassisch NPE) nur die kryptische Assertion
     * "text must not be null" und verschluckt die echte Ursache.
     */
    private <T> T demaskiert(String tool, Supplier<T> body) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            log.error("MCP {} fehlgeschlagen: {}", tool, msg != null ? msg : e.getClass().getName(), e);
            if (msg == null || msg.isBlank()) {
                throw new IllegalStateException(tool + " fehlgeschlagen: " + e.getClass().getName()
                        + " (Details im Server-Log)", e);
            }
            throw e;
        }
    }

    // ── DTOs ───────────────────────────────────────────────

    /** Eine Karte mit allen Kommentaren — das volle Bild zu einer Karte. */
    public record CardInfo(CardJson karte, List<CommentJson> kommentare) {
    }
}
