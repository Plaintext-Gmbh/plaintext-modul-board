/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.apitoken.ApiTokenValidatorService;
import ch.plaintext.apitoken.IApiTokenService;
import ch.plaintext.apitoken.ITokenValidationOutcome;
import ch.plaintext.board.BoardDeckJson.BoardJson;
import ch.plaintext.board.BoardDeckJson.CardJson;
import ch.plaintext.board.BoardDeckJson.CommentJson;
import ch.plaintext.board.BoardDeckJson.LabelJson;
import ch.plaintext.board.BoardDeckJson.StackJson;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Deck-kompatible REST-API (Karte 2193): gleiche Pfade und JSON-Formate wie die
 * Nextcloud-Deck-API v1.0, damit {@code ~/scripts/deck} mit einem Backend-Schalter
 * ({@code backend=nextcloud|app}) dieselben Befehle gegen die App laufen laesst.
 * Autorisierung wie bei den anderen REST-APIs: Bearer API-Token
 * ({@code ApiTokenValidatorService}, Schreibzugriffe brauchen Scope WRITE).
 * <p>
 * Der Karten-Verschiebe-Endpunkt des Skripts ({@code /cards/{cid}/reorder}) liegt wie im Deck
 * ausserhalb der Stack-Pfade — das Skript baut ihn aus {@code INTERN}/App-Basis-URL.
 */
@RestController
@RequestMapping("/api/deck/v1")
@RequiredArgsConstructor
@Slf4j
public class BoardDeckController {

    private final BoardDeckService service;
    private final BoardImportService importService;
    private final ApiTokenValidatorService apiTokenValidatorService;

    // ── Boards und Stapel ──────────────────────────────────

    @GetMapping("/boards")
    public ResponseEntity<?> boards(@RequestHeader(value = "Authorization", required = false) String auth,
                                    HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.boards(m)));
    }

    @GetMapping("/boards/{bid}")
    public ResponseEntity<?> board(@PathVariable long bid,
                                   @RequestHeader(value = "Authorization", required = false) String auth,
                                   HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.board(bid, m)));
    }

    @GetMapping("/boards/{bid}/stacks")
    public ResponseEntity<?> stacks(@PathVariable long bid,
                                    @RequestHeader(value = "Authorization", required = false) String auth,
                                    HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.stacks(bid, m)));
    }

    @PostMapping("/boards/{bid}/stacks")
    public ResponseEntity<?> createStack(@PathVariable long bid, @RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "Authorization", required = false) String auth,
                                         HttpServletRequest request) {
        return aufruf(auth, request, m ->
                ResponseEntity.status(HttpStatus.CREATED)
                        .body(service.createStack(bid, (String) body.get("title"), m)));
    }

    // ── Karten ─────────────────────────────────────────────

    @GetMapping("/boards/{bid}/stacks/{sid}/cards")
    public ResponseEntity<?> cards(@PathVariable long bid, @PathVariable long sid,
                                   @RequestHeader(value = "Authorization", required = false) String auth,
                                   HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.cards(bid, sid, m)));
    }

    @PostMapping("/boards/{bid}/stacks/{sid}/cards")
    public ResponseEntity<?> createCard(@PathVariable long bid, @PathVariable long sid,
                                        @RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "Authorization", required = false) String auth,
                                        HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.status(HttpStatus.CREATED).body(service.createCard(
                bid, sid, (String) body.get("title"), (String) body.get("description"),
                zahl(body.get("order")), (String) body.get("duedate"), m)));
    }

    @GetMapping("/boards/{bid}/stacks/{sid}/cards/{cid}")
    public ResponseEntity<?> card(@PathVariable long bid, @PathVariable long sid, @PathVariable long cid,
                                  @RequestHeader(value = "Authorization", required = false) String auth,
                                  HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.card(bid, sid, cid, m)));
    }

    @PutMapping("/boards/{bid}/stacks/{sid}/cards/{cid}")
    public ResponseEntity<?> updateCard(@PathVariable long bid, @PathVariable long sid, @PathVariable long cid,
                                        @RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "Authorization", required = false) String auth,
                                        HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.updateCard(
                bid, sid, cid, (String) body.get("title"), (String) body.get("description"),
                zahl(body.get("order")), (String) body.get("duedate"), m)));
    }

    @DeleteMapping("/boards/{bid}/stacks/{sid}/cards/{cid}")
    public ResponseEntity<?> deleteCard(@PathVariable long bid, @PathVariable long sid, @PathVariable long cid,
                                        @RequestHeader(value = "Authorization", required = false) String auth,
                                        HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            service.deleteCard(bid, sid, cid, m);
            return ResponseEntity.noContent().build();
        });
    }

    @PutMapping("/boards/{bid}/stacks/{sid}/cards/{cid}/assignLabel")
    public ResponseEntity<?> assignLabel(@PathVariable long bid, @PathVariable long sid, @PathVariable long cid,
                                         @RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "Authorization", required = false) String auth,
                                         HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            service.assignLabel(bid, sid, cid, ((Number) body.get("labelId")).longValue(), m);
            return ResponseEntity.ok(Map.of("ok", true));
        });
    }

    @PutMapping("/boards/{bid}/stacks/{sid}/cards/{cid}/removeLabel")
    public ResponseEntity<?> removeLabel(@PathVariable long bid, @PathVariable long sid, @PathVariable long cid,
                                         @RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "Authorization", required = false) String auth,
                                         HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            service.removeLabel(bid, sid, cid, ((Number) body.get("labelId")).longValue(), m);
            return ResponseEntity.ok(Map.of("ok", true));
        });
    }

    @PostMapping("/boards/{bid}/labels")
    public ResponseEntity<?> createLabel(@PathVariable long bid, @RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "Authorization", required = false) String auth,
                                         HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createLabel(bid, (String) body.get("title"), (String) body.get("color"), m)));
    }

    // ── Migration (Import aus dem Nextcloud-Deck, Karte 2193 Phase 2) ──

    /**
     * Importiert eine Karte mit fester Id aus dem Nextcloud-Deck (idempotent, nur fuer die
     * Migration — die Karten-Ids bleiben 1:1 erhalten, weil Agenten und Memories auf Nummern
     * verweisen). Nicht im Deck-Skript genutzt.
     */
    @PostMapping("/boards/{bid}/import/cards")
    public ResponseEntity<?> importCard(@PathVariable long bid, @RequestBody BoardDeckJson.CardImportJson karte,
                                        @RequestHeader(value = "Authorization", required = false) String auth,
                                        HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            IApiTokenService.ApiTokenValidationResult v = token(auth, request);
            int kommentare = importService.importCard(bid, karte, m, v.email());
            return ResponseEntity.ok(Map.of("id", karte.id, "kommentare", kommentare));
        });
    }

    @PostMapping("/boards")
    public ResponseEntity<?> createBoard(@RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "Authorization", required = false) String auth,
                                         HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createBoard((String) body.get("title"), m)));
    }

    // ── Verschieben und Kommentare (Pfade wie im Deck-Skript) ──

    @PutMapping("/cards/{cid}/reorder")
    public ResponseEntity<?> reorder(@PathVariable long cid, @RequestBody Map<String, Object> body,
                                     @RequestHeader(value = "Authorization", required = false) String auth,
                                     HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            service.moveCard(cid, ((Number) body.get("stackId")).longValue(),
                    zahl(body.getOrDefault("order", 0)), m);
            return ResponseEntity.ok(Map.of("ok", true));
        });
    }

    @GetMapping("/cards/{cid}/comments")
    public ResponseEntity<?> comments(@PathVariable long cid,
                                      @RequestHeader(value = "Authorization", required = false) String auth,
                                      HttpServletRequest request) {
        return aufruf(auth, request, m -> ResponseEntity.ok(service.comments(cid, m)));
    }

    @PostMapping("/cards/{cid}/comments")
    public ResponseEntity<?> addComment(@PathVariable long cid, @RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "Authorization", required = false) String auth,
                                        HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            IApiTokenService.ApiTokenValidationResult v = token(auth, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(service.addComment(
                    cid, (String) body.get("message"), String.valueOf(v.userId()), autor(v), m));
        });
    }

    @DeleteMapping("/cards/{cid}/comments/{coid}")
    public ResponseEntity<?> deleteComment(@PathVariable long cid, @PathVariable long coid,
                                           @RequestHeader(value = "Authorization", required = false) String auth,
                                           HttpServletRequest request) {
        return aufruf(auth, request, m -> {
            service.deleteComment(cid, coid, m);
            return ResponseEntity.noContent().build();
        });
    }

    // ── Helfer ─────────────────────────────────────────────

    private interface BoardAufruf {
        ResponseEntity<?> ausfuehren(String mandat);
    }

    /** Token pruefen, Aufruf im Mandats-Kontext ausfuehren, Fehler als Status. */
    private ResponseEntity<?> aufruf(String auth, HttpServletRequest request, BoardAufruf aufruf) {
        ITokenValidationOutcome outcome;
        try {
            outcome = apiTokenValidatorService.validateRequest(auth, request.getRequestURI());
        } catch (Exception e) {
            log.warn("API: Token-Validierung fehlgeschlagen: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (outcome.hasError()) {
            return outcome.getErrorResponse();
        }
        IApiTokenService.ApiTokenValidationResult v = outcome.getValidation();
        SecurityContext vorher = SecurityContextHolder.getContext();
        try {
            SecurityContext eigener = SecurityContextHolder.createEmptyContext();
            eigener.setAuthentication(new UsernamePasswordAuthenticationToken(v.email(), null, List.of(
                    new SimpleGrantedAuthority("PROPERTY_MANDAT_" + v.mandat().toUpperCase(Locale.ROOT)),
                    new SimpleGrantedAuthority("PROPERTY_MYUSERID_" + v.userId()))));
            SecurityContextHolder.setContext(eigener);
            return aufruf.ausfuehren(v.mandat());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } finally {
            SecurityContextHolder.setContext(vorher);
        }
    }

    /**
     * Karte 2342 (Entscheid Daniel): die Agenten-Tokens ({@code agent-kimi1}, ...) laufen unter Daniels Benutzer;
     * als Autor eines Kommentars zaehlt dann der Tokenname, sonst stuende ueberall Daniel.
     */
    static String autor(IApiTokenService.ApiTokenValidationResult v) {
        String name = v.tokenName();
        return name != null && name.startsWith("agent-") ? name : v.email();
    }

    private IApiTokenService.ApiTokenValidationResult token(String auth, HttpServletRequest request) {
        ITokenValidationOutcome outcome = apiTokenValidatorService.validateRequest(auth, request.getRequestURI());
        return outcome.getValidation();
    }

    private Integer zahl(Object wert) {
        return wert instanceof Number n ? n.intValue() : null;
    }
}
