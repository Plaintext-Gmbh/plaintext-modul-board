/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.board.BoardDeckJson.BoardJson;
import ch.plaintext.board.BoardDeckJson.CardJson;
import ch.plaintext.board.BoardDeckJson.CommentJson;
import ch.plaintext.board.BoardDeckJson.StackJson;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Backing Bean der Kanban-Seite (Karte 2193, Phase 4): Stapel-Spalten mit Drag and Drop
 * (board.js meldet Ziel/Karte per remoteCommand), Kartendetail mit Markdown-Vorschau und
 * Kommentaren, Filter nach Label und Text, WIP-Warnung je Stapel. Session-scoped wie die
 * anderen Seiten-Beans (kein @ViewScoped); alles laeuft ueber den {@link BoardDeckService}
 * im Mandat des angemeldeten Benutzers.
 */
@Component("boardBean")
@Scope("session")
public class BoardBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Feldinjektion mit transient (KEIN final): nach Session-Deserialisierung setzt Spring nach. */
    @Autowired
    private transient BoardDeckService service;
    @Autowired
    private transient BoardLabelRepository labelRepository;
    @Autowired
    private transient BoardMarkdownService markdown;
    @Autowired
    private transient PlaintextSecurity security;

    /** Board-Auswahl per ?board=<id> — ohne Parameter das einzige Board des Mandats. */
    @Getter @Setter
    private Long boardId;

    @Getter
    private String boardTitel;

    @Getter
    private List<StackJson> stapel = List.of();

    /** Ungefilterte Kartenanzahl je Stapel (WIP-Pruefung darf der Filter nicht verstecken). */
    private Map<Long, Integer> anzahlUngefiltert = Map.of();

    @Getter
    private List<BoardLabel> labels = List.of();

    @Getter @Setter
    private String filterText = "";

    /** null = alle Labels. */
    @Getter @Setter
    private Long filterLabelId;

    // ── Kartendetail ───────────────────────────────────────

    @Getter
    private Long detailKarteId;

    @Getter @Setter
    private String detailTitel;

    @Getter @Setter
    private String detailBeschreibung;

    @Getter
    private String detailHtml = "";

    @Getter
    private List<CommentJson> detailKommentare = List.of();

    @Getter @Setter
    private String neuerKommentar;

    private long detailStackId;

    public BoardBean() {
    }

    // ── Laden und Filter ───────────────────────────────────

    public void laden() {
        String mandat = security.getMandat();
        if (boardId == null) {
            List<BoardJson> boards = service.boards(mandat);
            if (boards.size() != 1) {
                boardTitel = null;
                stapel = List.of();
                labels = List.of();
                return;
            }
            boardId = boards.get(0).id;
        }
        BoardJson b = service.board(boardId, mandat);
        boardTitel = b.title;
        labels = labelRepository.findByBoardId(boardId);
        List<StackJson> roh = service.stacks(boardId, mandat);
        anzahlUngefiltert = new HashMap<>();
        for (StackJson s : roh) {
            anzahlUngefiltert.put(s.id, s.cards.size());
        }
        stapel = roh.stream()
                .map(s -> {
                    StackJson j = new StackJson();
                    j.id = s.id;
                    j.title = s.title;
                    j.order = s.order;
                    j.wipLimit = s.wipLimit;
                    j.cards = s.cards.stream().filter(this::passtFilter).toList();
                    return j;
                })
                .toList();
    }

    private boolean passtFilter(CardJson karte) {
        if (filterLabelId != null
                && karte.labels.stream().noneMatch(l -> Objects.equals(l.id, filterLabelId))) {
            return false;
        }
        if (filterText != null && !filterText.isBlank()) {
            String nadel = filterText.trim().toLowerCase(Locale.ROOT);
            String heuhaufen = (karte.title + "\n" + karte.description).toLowerCase(Locale.ROOT);
            if (!heuhaufen.contains(nadel)) {
                return false;
            }
        }
        return true;
    }

    /** WIP-Pruefung auf dem UNgefilterten Bestand: Ueberschreitung darf der Filter nicht verstecken. */
    public boolean wipUeberschritten(StackJson stapel) {
        return stapel.wipLimit != null
                && anzahlUngefiltert.getOrDefault(stapel.id, 0) > stapel.wipLimit;
    }

    // ── Aktionen ───────────────────────────────────────────

    /** Drag and Drop (board.js) und Verschieben-Buttons im Kartendetail. */
    public void verschieben(long karteId, long zielStackId) {
        String mandat = security.getMandat();
        int ende = service.cards(boardId, zielStackId, mandat).size();
        service.moveCard(karteId, zielStackId, ende, mandat);
        if (detailKarteId != null && detailKarteId == karteId) {
            detailKommentare = service.comments(karteId, mandat);
        }
        laden();
    }

    /** Einstieg fuer den remoteCommand: Parameter heissen 'karte' und 'ziel'. */
    public void verschiebenParam() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) {
            return;
        }
        Map<String, String> params = ctx.getExternalContext().getRequestParameterMap();
        verschieben(Long.parseLong(params.get("karte")), Long.parseLong(params.get("ziel")));
    }

    public void detailOeffnen(long karteId) {
        String mandat = security.getMandat();
        CardJson karte = service.card(karteId, mandat);
        detailKarteId = karte.id;
        detailStackId = karte.stackId;
        detailTitel = karte.title;
        detailBeschreibung = karte.description;
        detailHtml = markdown.render(karte.description);
        detailKommentare = service.comments(karteId, mandat);
        neuerKommentar = null;
    }

    /** Neumodus: der zu fuellende Stapel (Kartendetail als Anlage-Dialog genutzt). */
    private long neuStackId;

    /** Legt eine Karte im Stapel neu an (Detail-Dialog im Neumodus, Karte 2193 Phase 4). */
    public void neuOeffnen(long stackId) {
        neuStackId = stackId;
        detailKarteId = null;
        detailTitel = "";
        detailBeschreibung = "";
        detailHtml = "";
        detailKommentare = List.of();
        detailStackId = stackId;
        neuerKommentar = null;
    }

    public void detailSpeichern() {
        String mandat = security.getMandat();
        if (detailKarteId == null) {
            if (detailTitel == null || detailTitel.isBlank()) {
                return;
            }
            service.createCard(neuStackId, detailTitel.trim(),
                    detailBeschreibung == null || detailBeschreibung.isBlank() ? null
                            : detailBeschreibung.trim(),
                    null, null, mandat);
        } else {
            service.updateCard(boardId, detailStackId, detailKarteId, detailTitel, detailBeschreibung,
                    null, null, mandat);
            detailHtml = markdown.render(detailBeschreibung);
        }
        laden();
    }

    public void kommentarSpeichern() {
        if (detailKarteId == null || neuerKommentar == null || neuerKommentar.isBlank()) {
            return;
        }
        service.addComment(detailKarteId, neuerKommentar.trim(), String.valueOf(security.getId()),
                security.getUser(), security.getMandat());
        neuerKommentar = null;
        detailKommentare = service.comments(detailKarteId, security.getMandat());
    }

    public void detailLoeschen() {
        if (detailKarteId == null) {
            return;
        }
        service.deleteCard(boardId, detailStackId, detailKarteId, security.getMandat());
        detailKarteId = null;
        laden();
    }

    public void schliessen() {
        detailKarteId = null;
    }

    /** Stack der geoefneten Karte — die Verschieben-Buttons blenden den aktuellen Stapel ab. */
    public long getDetailStackId() {
        return detailStackId;
    }
}
