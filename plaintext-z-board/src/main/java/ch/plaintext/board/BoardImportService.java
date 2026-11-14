/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Import migrierter Karten aus dem Nextcloud-Deck (Karte 2193, Phase 2). Die Karten-IDs muessen
 * 1:1 uebernommen werden — Agenten und Memories verweisen auf Karten-Nummern. JPA-IDENTITY
 * laesst keine vorgegebenen Ids zu, deshalb laeuft der Import als SQL-Upsert (INSERT ... ON
 * CONFLICT), idempotent fuer Probelauf und Nachimport. Anschliessend wird die Sequenz auf das
 * Maximum gesetzt, damit neue Karten kollisionsfrei weiterzaehlen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardImportService {

    private final JdbcTemplate jdbc;
    private final BoardRepository boardRepository;
    private final BoardStackRepository stackRepository;

    /**
     * Legt eine Karte mit fester Id (und ihren Labels und Kommentaren) an oder ersetzt sie.
     *
     * @return Anzahl importierter Kommentare
     */
    @Transactional
    public int importCard(long boardId, BoardDeckJson.CardImportJson karte, String mandat, String actor) {
        Board board = boardRepository.findById(boardId)
                .filter(b -> mandat.equals(b.getMandat()))
                .orElseThrow(() -> new NoSuchElementException("Board " + boardId + " nicht im Mandat " + mandat));
        BoardStack stack = stackRepository.findById(karte.stackId)
                .filter(s -> s.getBoard().getId().equals(board.getId()))
                .orElseThrow(() -> new NoSuchElementException("Stapel " + karte.stackId + " nicht auf Board " + boardId));

        Timestamp jetzt = Timestamp.from(Instant.now());
        Timestamp angelegt = karte.createdDate == null ? jetzt : Timestamp.from(Instant.parse(karte.createdDate));
        Timestamp faellig = karte.duedate == null ? null : Timestamp.from(Instant.parse(karte.duedate));

        // Karte mit fester Id — idempotent (Probelauf/Nachimport erzeugen keine Dubletten).
        jdbc.update("""
                        INSERT INTO board_card (ID, CREATED_BY, CREATED_DATE, DELETED, LAST_MODIFIED_BY,
                            LAST_MODIFIED_DATE, MANDAT, TAGS, BOARD_ID, STACK_ID, TITLE, DESCRIPTION,
                            SORT_INDEX, DUE_DATE, ARCHIVED)
                        VALUES (?, ?, ?, FALSE, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (ID) DO UPDATE SET
                            LAST_MODIFIED_BY = EXCLUDED.LAST_MODIFIED_BY,
                            LAST_MODIFIED_DATE = EXCLUDED.LAST_MODIFIED_DATE,
                            STACK_ID = EXCLUDED.STACK_ID,
                            TITLE = EXCLUDED.TITLE,
                            DESCRIPTION = EXCLUDED.DESCRIPTION,
                            SORT_INDEX = EXCLUDED.SORT_INDEX,
                            DUE_DATE = EXCLUDED.DUE_DATE,
                            ARCHIVED = EXCLUDED.ARCHIVED
                        """,
                karte.id, actor, angelegt, actor, jetzt, mandat, boardId, stack.getId(),
                karte.title, karte.description == null ? "" : karte.description,
                karte.order, faellig, karte.archived);

        // Labels je Titel sicherstellen und zuordnen (Zuordnung idempotent ersetzt).
        jdbc.update("DELETE FROM board_card_labels WHERE BOARD_CARD_ID = ?", karte.id);
        if (karte.labels != null) {
            for (BoardDeckJson.LabelRefJson ref : karte.labels) {
                Long labelId = findeOderLegeLabelAn(boardId, ref, mandat, actor, jetzt);
                jdbc.update("INSERT INTO board_card_labels (BOARD_CARD_ID, LABELS_ID) VALUES (?, ?)", karte.id, labelId);
            }
        }

        // Kommentare in der Reihenfolge ersetzen (neue Ids — nichts verweist auf Kommentar-Nummern).
        jdbc.update("DELETE FROM board_comment WHERE CARD_ID = ?", karte.id);
        int anzahl = 0;
        if (karte.comments != null) {
            for (BoardDeckJson.CommentImportJson c : karte.comments) {
                Timestamp kommentarZeit = c.createdDate == null ? jetzt : Timestamp.from(Instant.parse(c.createdDate));
                jdbc.update("""
                                INSERT INTO board_comment (CREATED_BY, CREATED_DATE, DELETED, LAST_MODIFIED_BY,
                                    LAST_MODIFIED_DATE, MANDAT, TAGS, CARD_ID, MESSAGE, ACTOR_ID, ACTOR_DISPLAY_NAME)
                                VALUES (?, ?, FALSE, ?, ?, ?, NULL, ?, ?, ?, ?)
                                """,
                        actor, kommentarZeit, actor, kommentarZeit, mandat, karte.id,
                        c.message, str(c.actorId, actor), str(c.actorDisplayName, actor));
                anzahl++;
            }
        }

        // Sequenzen auf das Maximum ziehen — neue Karten/Kommentare/Labels zaehlen kollisionsfrei weiter.
        jdbc.execute("SELECT setval('board_card_id_seq', (SELECT COALESCE(MAX(ID), 1) FROM board_card))");
        jdbc.execute("SELECT setval('board_comment_id_seq', (SELECT COALESCE(MAX(ID), 1) FROM board_comment))");
        jdbc.execute("SELECT setval('board_label_id_seq', (SELECT COALESCE(MAX(ID), 1) FROM board_label))");

        log.info("Board-Import: Karte {} ({}) importiert, {} Kommentare", karte.id, karte.title, anzahl);
        return anzahl;
    }

    private Long findeOderLegeLabelAn(long boardId, BoardDeckJson.LabelRefJson ref, String mandat,
                                      String actor, Timestamp jetzt) {
        List<Map<String, Object>> vorhanden = jdbc.queryForList(
                "SELECT ID FROM board_label WHERE BOARD_ID = ? AND TITLE = ?", boardId, ref.title);
        if (!vorhanden.isEmpty()) {
            return ((Number) vorhanden.get(0).get("ID")).longValue();
        }
        jdbc.update("""
                        INSERT INTO board_label (CREATED_BY, CREATED_DATE, DELETED, LAST_MODIFIED_BY,
                            LAST_MODIFIED_DATE, MANDAT, TAGS, BOARD_ID, TITLE, COLOR)
                        VALUES (?, ?, FALSE, ?, ?, ?, NULL, ?, ?, ?)
                        """,
                actor, jetzt, actor, jetzt, mandat, boardId, ref.title, ref.color == null ? "" : ref.color);
        return jdbc.queryForObject("SELECT MAX(ID) FROM board_label", Long.class);
    }

    private String str(String wert, String ersatz) {
        return wert == null || wert.isBlank() ? ersatz : wert;
    }
}
