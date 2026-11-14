package ch.plaintext.board;

import ch.plaintext.apitoken.ApiTokenValidatorService;
import ch.plaintext.apitoken.IApiTokenService;
import ch.plaintext.apitoken.ITokenValidationOutcome;
import ch.plaintext.boot.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
 * Karte 2193, Phase 1b/1c: die Deck-kompatible REST-API — ein Durchgang ueber die Endpunkte,
 * die {@code ~/scripts/deck} nutzt (Boards, Stapel mit eingebetteten Karten, Karte anlegen/
 * lesen/aendern/loeschen, Label, Verschieben, Kommentare). Deck-JSON-Formate: {@code order},
 * {@code description}, eingebettete {@code labels}, {@code cards} am Stapel.
 */
@Import(BoardDeckApiTest.TokenStub.class)
class BoardDeckApiTest extends BaseIntegrationTest {

    /** Nimmt jeden Bearer an, Mandat TEST — der Test prueft die API, nicht die Token-Logik. */
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
    long vorrat;
    long todo;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.basePath = "";

        // Der Kontext (und die DB) lebt ueber alle Tests — jeder Test beginnt leer.
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
        vorrat = stapel(b, "vorrat", 0);
        todo = stapel(b, "todo", 1);
    }

    private long stapel(Board b, String titel, int ordnung) {
        BoardStack s = new BoardStack();
        s.setBoard(b);
        s.setTitle(titel);
        s.setSortIndex(ordnung);
        return stackRepository.save(s).getId();
    }

    private io.restassured.specification.RequestSpecification api() {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer test-token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deckEndpunkteRunde() {
        // Boards
        List<Map<String, Object>> boards = api().get("/api/deck/v1/boards").then().statusCode(200)
                .extract().body().jsonPath().getList("$");
        assertEquals(1, boards.size());
        assertEquals("Plaintext-Backlog", boards.get(0).get("title"));

        // Stapel mit eingebetteten Karten (Deck-Form)
        List<Map<String, Object>> stapel = api().get("/api/deck/v1/boards/" + boardId + "/stacks").then()
                .statusCode(200).extract().body().jsonPath().getList("$");
        assertEquals(2, stapel.size());
        assertEquals("vorrat", stapel.get(0).get("title"));
        assertTrue(stapel.get(0).containsKey("cards"));

        // Karte anlegen (deck new)
        Response angelegt = api().body(Map.of("title", "board-modul", "description", "## Auftrag\n\nText",
                        "order", 400))
                .post("/api/deck/v1/boards/" + boardId + "/stacks/" + vorrat + "/cards")
                .then().statusCode(201).extract().response();
        long cid = angelegt.jsonPath().getLong("id");
        assertEquals(400, angelegt.jsonPath().getInt("order"));
        assertEquals("board-modul", angelegt.jsonPath().getString("title"));

        // Karte lesen (deck show) — Beschreibung byte-gleich
        Response gelesen = api().get("/api/deck/v1/boards/" + boardId + "/stacks/" + vorrat + "/cards/" + cid)
                .then().statusCode(200).extract().response();
        assertEquals("## Auftrag\n\nText", gelesen.jsonPath().getString("description"));
        assertEquals(vorrat, gelesen.jsonPath().getLong("stackId"));

        // Beschreibung anhaengen (deck append / Slot 413: update_card description)
        String erweitert = "## Auftrag\n\nText\n\n_[slot] kimi1 — 14.11.2026 20:00 utc=1763148000_";
        api().body(Map.of("description", erweitert))
                .put("/api/deck/v1/boards/" + boardId + "/stacks/" + vorrat + "/cards/" + cid)
                .then().statusCode(200);
        api().get("/api/deck/v1/boards/" + boardId + "/stacks/" + vorrat + "/cards/" + cid)
                .then().statusCode(200).body("description", org.hamcrest.Matchers.is(erweitert));

        // Verschieben (deck move / reorder)
        api().body(Map.of("cardId", cid, "stackId", todo, "order", 5))
                .put("/api/deck/v1/cards/" + cid + "/reorder").then().statusCode(200);
        api().get("/api/deck/v1/boards/" + boardId + "/stacks/" + todo + "/cards/" + cid)
                .then().statusCode(200)
                .body("stackId", org.hamcrest.Matchers.is((int) todo))
                .body("order", org.hamcrest.Matchers.is(5));

        // Label anlegen und zuweisen (deck label add)
        long labelId = api().body(Map.of("title", "kimi", "color", "ffcc00"))
                .post("/api/deck/v1/boards/" + boardId + "/labels").then().statusCode(201)
                .extract().jsonPath().getLong("id");
        api().body(Map.of("labelId", labelId))
                .put("/api/deck/v1/boards/" + boardId + "/stacks/" + todo + "/cards/" + cid + "/assignLabel")
                .then().statusCode(200);
        api().get("/api/deck/v1/boards/" + boardId + "/stacks/" + todo + "/cards/" + cid)
                .then().statusCode(200)
                .body("labels.size()", org.hamcrest.Matchers.is(1))
                .body("labels[0].title", org.hamcrest.Matchers.is("kimi"));

        // Stapel-Liste enthaelt die Karte mit Label (deck list)
        List<Map<String, Object>> stapel2 = api().get("/api/deck/v1/boards/" + boardId + "/stacks").then()
                .statusCode(200).extract().body().jsonPath().getList("$");
        Map<String, Object> todoStack = stapel2.stream()
                .filter(s -> "todo".equals(s.get("title"))).findFirst().orElseThrow();
        List<Map<String, Object>> todoKarten = (List<Map<String, Object>>) todoStack.get("cards");
        assertEquals(1, todoKarten.size());
        assertEquals("board-modul", todoKarten.get(0).get("title"));

        // Kommentar anlegen, lesen, loeschen (deck comments/comment-del)
        long commentId = api().body(Map.of("message", "Hallo"))
                .post("/api/deck/v1/cards/" + cid + "/comments").then().statusCode(201)
                .extract().jsonPath().getLong("id");
        assertEquals(1, api().get("/api/deck/v1/cards/" + cid + "/comments").then().statusCode(200)
                .extract().jsonPath().getList("$").size());
        api().delete("/api/deck/v1/cards/" + cid + "/comments/" + commentId).then().statusCode(204);
        assertEquals(0, api().get("/api/deck/v1/cards/" + cid + "/comments").then().statusCode(200)
                .extract().jsonPath().getList("$").size());

        // Karte loeschen (deck delete)
        api().delete("/api/deck/v1/boards/" + boardId + "/stacks/" + todo + "/cards/" + cid)
                .then().statusCode(204);
        assertEquals(0, cardRepository.findByBoardId(boardId).size());
        assertNotNull(commentRepository);
    }

    @Test
    void fremdesBoardIstNichtSichtbar() {
        Board fremd = new Board();
        fremd.setTitle("anderes-mandat");
        fremd.setMandat("ANDERS");
        fremd = boardRepository.save(fremd);
        api().get("/api/deck/v1/boards/" + fremd.getId()).then().statusCode(404);
    }
}
