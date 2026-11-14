package ch.plaintext.board;

import ch.plaintext.apitoken.ApiTokenValidatorService;
import ch.plaintext.apitoken.IApiTokenService;
import ch.plaintext.apitoken.ITokenValidationOutcome;
import ch.plaintext.boot.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 2193, Phase 2: Import aus dem Nextcloud-Deck — Karte mit fester Id (2193), Labels je
 * Titel, Kommentare mit urspruenglichem Zeitstempel. Idempotent (zweiter Import ersetzt statt
 * zu duplizieren) und die Sequenz zaehlt danach hinter der hoechsten Import-Id weiter.
 */
@Import(BoardImportTest.TokenStub.class)
class BoardImportTest extends BaseIntegrationTest {

    static class TokenStub {
        @Bean
        @Primary
        ApiTokenValidatorService apiTokenValidatorService() {
            return (auth, uri) -> new ITokenValidationOutcome() {
                @Override
                public boolean hasError() {
                    return false;
                }

                @Override
                public org.springframework.http.ResponseEntity<?> getErrorResponse() {
                    return org.springframework.http.ResponseEntity.status(401).build();
                }

                @Override
                public IApiTokenService.ApiTokenValidationResult getValidation() {
                    return new IApiTokenService.ApiTokenValidationResult(1L, "TEST",
                            "kimi1@example.test", "test", Instant.now().plusSeconds(3600), "WRITE");
                }
            };
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    BoardRepository boardRepository;
    @Autowired
    BoardStackRepository stackRepository;
    @Autowired
    BoardCardRepository cardRepository;
    @Autowired
    BoardCommentRepository commentRepository;
    @Autowired
    BoardLabelRepository labelRepository;

    long boardId;
    long todo;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.basePath = "";
        commentRepository.deleteAll();
        cardRepository.deleteAll();
        stackRepository.deleteAll();
        labelRepository.deleteAll();
        boardRepository.deleteAll();

        Board b = new Board();
        b.setTitle("Plaintext-Backlog");
        b.setMandat("TEST");
        b = boardRepository.save(b);
        boardId = b.getId();
        BoardStack s = new BoardStack();
        s.setBoard(b);
        s.setTitle("todo");
        s.setSortIndex(0);
        s.setWipLimit(5);
        todo = stackRepository.save(s).getId();
    }

    private Map<String, Object> importPayload() {
        return Map.of(
                "id", 2193,
                "stackId", todo,
                "title", "board-modul-kanban-ersetzt-nextcloud-deck",
                "description", "## Auftrag Daniel\n\nText mit Umlauten äöü",
                "order", 400,
                "createdDate", "2026-11-14T18:00:00Z",
                "labels", List.of(Map.of("title", "kimi", "color", "ffcc00")),
                "comments", List.of(
                        Map.of("message", "erster Kommentar", "actorId", "worker",
                                "actorDisplayName", "worker", "createdDate", "2026-11-14T18:05:00Z"),
                        Map.of("message", "zweiter Kommentar", "actorId", "chef",
                                "actorDisplayName", "chef", "createdDate", "2026-11-14T18:06:00Z")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void importMitFesterIdUndIdempotenz() {
        // Erstimport
        given().contentType(ContentType.JSON).header("Authorization", "Bearer test-token")
                .body(importPayload())
                .post("/api/deck/v1/boards/" + boardId + "/import/cards")
                .then().statusCode(200);

        // Karte hat exakt die Id 2193 mit Beschreibung und Label
        BoardCard karte = cardRepository.findById(2193L).orElseThrow();
        assertEquals("board-modul-kanban-ersetzt-nextcloud-deck", karte.getTitle());
        assertEquals("## Auftrag Daniel\n\nText mit Umlauten äöü", karte.getDescription());
        assertEquals(todo, karte.getStack().getId());
        assertEquals(400, karte.getSortIndex());
        assertEquals(1, karte.getLabels().size());
        assertEquals("kimi", karte.getLabels().iterator().next().getTitle());

        // Kommentare in Reihenfolge mit Zeitstempeln
        List<BoardComment> kommentare = commentRepository.findByCardIdOrderByCreatedDate(2193L);
        assertEquals(2, kommentare.size());
        assertEquals("erster Kommentar", kommentare.get(0).getMessage());
        assertEquals("worker", kommentare.get(0).getActorId());
        assertNotNull(kommentare.get(0).getCreatedDate());

        // API liefert die importierte Karte im Deck-Format
        given().header("Authorization", "Bearer test-token")
                .get("/api/deck/v1/boards/" + boardId + "/stacks/" + todo + "/cards/2193")
                .then().statusCode(200)
                .body("title", org.hamcrest.Matchers.is("board-modul-kanban-ersetzt-nextcloud-deck"))
                .body("order", org.hamcrest.Matchers.is(400))
                .body("labels.size()", org.hamcrest.Matchers.is(1));

        // Zweiter Import ersetzt (idempotent): immer noch genau 2 Kommentare, Label nicht doppelt
        given().contentType(ContentType.JSON).header("Authorization", "Bearer test-token")
                .body(importPayload())
                .post("/api/deck/v1/boards/" + boardId + "/import/cards")
                .then().statusCode(200);
        assertEquals(2, commentRepository.findByCardIdOrderByCreatedDate(2193L).size());
        assertEquals(1, cardRepository.findByBoardId(boardId).size());

        // Sequenz zaehlt hinter 2193 weiter: neue Karte bekommt eine groessere Id
        long neueId = given().contentType(ContentType.JSON).header("Authorization", "Bearer test-token")
                .body(Map.of("title", "nach dem Import"))
                .post("/api/deck/v1/boards/" + boardId + "/stacks/" + todo + "/cards")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        assertTrue(neueId > 2193, "neue Karte id=" + neueId + " muss > 2193 sein");
    }

    @Test
    void importAufFremdemBoardAbgelehnt() {
        Board fremd = new Board();
        fremd.setTitle("fremd");
        fremd.setMandat("ANDERS");
        fremd = boardRepository.save(fremd);
        given().contentType(ContentType.JSON).header("Authorization", "Bearer test-token")
                .body(importPayload())
                .post("/api/deck/v1/boards/" + fremd.getId() + "/import/cards")
                .then().statusCode(404);
        assertTrue(cardRepository.findById(2193L).isEmpty());
    }
}
