/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.board.BoardDeckJson.CardJson;
import ch.plaintext.board.BoardDeckJson.CommentJson;
import ch.plaintext.board.BoardDeckJson.LabelJson;
import ch.plaintext.board.BoardDeckJson.StackJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Fachlogik der Deck-kompatiblen REST-API (Karte 2193). Liest und schreibt ausschliesslich im
 * Mandat des API-Tokens; das Board des Mandanten ist das Kanban-Board (beim Plaintext-Backlog
 * das migrierte Nextcloud-Deck-Board).
 * <p>
 * Beschreibungs-Updates ({@code deck append}, Slot 413) sind bewusst einfach: letzter
 * Schreibende gewinnt, wie im Deck — die Slot-Logik verhindert Mehrfach-Besitzer zeitlich.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardDeckService {

    private final BoardRepository boardRepository;
    private final BoardStackRepository stackRepository;
    private final BoardCardRepository cardRepository;
    private final BoardLabelRepository labelRepository;
    private final BoardCommentRepository commentRepository;

    // ── Lesen ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BoardDeckJson.BoardJson> boards(String mandat) {
        return boardRepository.findByMandat(mandat).stream().map(BoardDeckJson.BoardJson::of).toList();
    }

    @Transactional(readOnly = true)
    public BoardDeckJson.BoardJson board(long boardId, String mandat) {
        return BoardDeckJson.BoardJson.of(board(boardId, mandat, true));
    }

    @Transactional(readOnly = true)
    public List<StackJson> stacks(long boardId, String mandat) {
        Board b = board(boardId, mandat, true);
        return stackRepository.findByBoardIdOrderBySortIndex(b.getId()).stream()
                .map(s -> StackJson.of(s, cardRepository.findByStackIdOrderBySortIndex(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CardJson> cards(long boardId, long stackId, String mandat) {
        stack(boardId, stackId, mandat);
        return cardRepository.findByStackIdOrderBySortIndex(stackId).stream().map(CardJson::of).toList();
    }

    @Transactional(readOnly = true)
    public CardJson card(long boardId, long stackId, long cardId, String mandat) {
        return CardJson.of(card(boardId, stackId, cardId, mandat, true));
    }

    @Transactional(readOnly = true)
    public List<CommentJson> comments(long cardId, String mandat) {
        BoardCard karte = cardById(cardId, mandat);
        return commentRepository.findByCardIdOrderByCreatedDate(karte.getId()).stream()
                .map(CommentJson::of).toList();
    }

    /** Eine Karte ohne Board-/Stapel-Kontext — bequemer Einstieg fuer MCP und Skripte. */
    @Transactional(readOnly = true)
    public CardJson card(long cardId, String mandat) {
        return CardJson.of(cardById(cardId, mandat));
    }

    // ── Schreiben ──────────────────────────────────────────

    @Transactional
    public BoardDeckJson.BoardJson createBoard(String title, String mandat) {
        Board b = new Board();
        b.setTitle(title);
        return BoardDeckJson.BoardJson.of(boardRepository.save(b));
    }

    @Transactional
    public StackJson createStack(long boardId, String title, String mandat) {
        Board b = board(boardId, mandat, true);
        int naechste = stackRepository.findByBoardId(b.getId()).stream().mapToInt(BoardStack::getSortIndex).max().orElse(-1) + 1;
        BoardStack s = new BoardStack();
        s.setBoard(b);
        s.setTitle(title);
        s.setSortIndex(naechste);
        return StackJson.of(stackRepository.save(s), List.of());
    }

    @Transactional
    public CardJson createCard(long boardId, long stackId, String title, String description, Integer order,
                               String duedate, String mandat) {
        BoardStack s = stack(boardId, stackId, mandat);
        BoardCard karte = new BoardCard();
        karte.setBoard(s.getBoard());
        karte.setStack(s);
        karte.setTitle(title);
        karte.setDescription(description == null ? "" : description);
        karte.setSortIndex(order == null ? 999 : order);
        karte.setDueDate(parseDueDate(duedate));
        return CardJson.of(cardRepository.save(karte));
    }

    /** Eine Karte direkt im Stapel anlegen — der Stapel bestimmt das Board (MCP-Einstieg). */
    @Transactional
    public CardJson createCard(long stackId, String title, String description, Integer order, String duedate,
                               String mandat) {
        BoardStack s = stackRepository.findById(stackId)
                .orElseThrow(() -> new NoSuchElementException("Stapel " + stackId + " nicht gefunden"));
        return createCard(s.getBoard().getId(), stackId, title, description, order, duedate, mandat);
    }

    /**
     * Haengt Text an die Beschreibung an ("deck append"): bestehender Text, Leerzeile, neuer Text.
     * Letzter Schreibende gewinnt wie im Deck — Reihenfolge ist Sache des Aufrufers (Slot 413).
     */
    @Transactional
    public CardJson appendDescription(long cardId, String text, String mandat) {
        BoardCard karte = cardById(cardId, mandat);
        String alt = karte.getDescription() == null ? "" : karte.getDescription();
        karte.setDescription(alt.isBlank() ? text : alt + "\n\n" + text);
        return CardJson.of(karte);
    }

    /**
     * Weist der Karte ein Label zu — nach Titel gesucht, auf dem Karten-Board angelegt, wenn es
     * noch fehlt ({@code farbe} nur beim Anlegen relevant, Deck-Hex ohne '#', Vorgabe grau).
     */
    @Transactional
    public CardJson setLabel(long cardId, String titel, String farbe, String mandat) {
        BoardCard karte = cardById(cardId, mandat);
        BoardLabel label = labelRepository.findByBoardId(karte.getBoard().getId()).stream()
                .filter(l -> l.getTitle() != null && l.getTitle().equalsIgnoreCase(titel))
                .findFirst()
                .orElseGet(() -> {
                    BoardLabel neu = new BoardLabel();
                    neu.setBoard(karte.getBoard());
                    neu.setTitle(titel);
                    neu.setColor(farbe == null || farbe.isBlank() ? "999999" : farbe);
                    return labelRepository.save(neu);
                });
        boolean schon = karte.getLabels().stream().anyMatch(l -> l.getId().equals(label.getId()));
        if (!schon) {
            karte.getLabels().add(label);
        }
        return CardJson.of(karte);
    }

    @Transactional
    public CardJson updateCard(long boardId, long stackId, long cardId, String title, String description,
                               Integer order, String duedate, String mandat) {
        BoardCard karte = card(boardId, stackId, cardId, mandat, true);
        if (title != null) {
            karte.setTitle(title);
        }
        if (description != null) {
            karte.setDescription(description);
        }
        if (order != null) {
            karte.setSortIndex(order);
        }
        if (duedate != null) {
            karte.setDueDate(parseDueDate(duedate));
        }
        return CardJson.of(karte);
    }

    @Transactional
    public void deleteCard(long boardId, long stackId, long cardId, String mandat) {
        BoardCard karte = card(boardId, stackId, cardId, mandat, true);
        commentRepository.findByCardIdOrderByCreatedDate(karte.getId()).forEach(commentRepository::delete);
        cardRepository.delete(karte);
    }

    @Transactional
    public void moveCard(long cardId, long zielStackId, int order, String mandat) {
        BoardCard karte = cardById(cardId, mandat);
        BoardStack ziel = stackRepository.findById(zielStackId)
                .filter(s -> karte.getBoard().getId().equals(s.getBoard().getId()))
                .orElseThrow(() -> new NoSuchElementException("Ziel-Stapel " + zielStackId + " nicht auf dem Board"));
        karte.setStack(ziel);
        karte.setSortIndex(order);
    }

    @Transactional
    public void assignLabel(long boardId, long stackId, long cardId, long labelId, String mandat) {
        BoardCard karte = card(boardId, stackId, cardId, mandat, true);
        BoardLabel label = labelRepository.findById(labelId)
                .filter(l -> karte.getBoard().getId().equals(l.getBoard().getId()))
                .orElseThrow(() -> new NoSuchElementException("Label " + labelId + " nicht auf dem Board"));
        karte.getLabels().add(label);
    }

    @Transactional
    public void removeLabel(long boardId, long stackId, long cardId, long labelId, String mandat) {
        BoardCard karte = card(boardId, stackId, cardId, mandat, true);
        karte.getLabels().removeIf(l -> l.getId() == labelId);
    }

    @Transactional
    public LabelJson createLabel(long boardId, String title, String color, String mandat) {
        Board b = board(boardId, mandat, true);
        BoardLabel l = new BoardLabel();
        l.setBoard(b);
        l.setTitle(title);
        l.setColor(color);
        return LabelJson.of(labelRepository.save(l));
    }

    @Transactional
    public CommentJson addComment(long cardId, String message, String actorId, String actorDisplayName,
                                  String mandat) {
        BoardCard karte = cardById(cardId, mandat);
        BoardComment c = new BoardComment();
        c.setCard(karte);
        c.setMessage(message);
        c.setActorId(actorId);
        c.setActorDisplayName(actorDisplayName);
        return CommentJson.of(commentRepository.save(c));
    }

    @Transactional
    public void deleteComment(long cardId, long commentId, String mandat) {
        BoardCard karte = cardById(cardId, mandat);
        BoardComment c = commentRepository.findById(commentId)
                .filter(x -> karte.getId().equals(x.getCard().getId()))
                .orElseThrow(() -> new NoSuchElementException("Kommentar " + commentId + " nicht an Karte " + cardId));
        commentRepository.delete(c);
    }

    // ── Helfer ─────────────────────────────────────────────

    private Board board(long boardId, String mandat, boolean pflicht) {
        Board b = boardRepository.findById(boardId).filter(x -> mandat.equals(x.getMandat())).orElse(null);
        if (b == null && pflicht) {
            throw new NoSuchElementException("Board " + boardId + " nicht im Mandat " + mandat);
        }
        return b;
    }

    private BoardStack stack(long boardId, long stackId, String mandat) {
        board(boardId, mandat, true);
        return stackRepository.findById(stackId)
                .filter(s -> s.getBoard().getId() == boardId)
                .orElseThrow(() -> new NoSuchElementException("Stapel " + stackId + " nicht auf Board " + boardId));
    }

    private BoardCard card(long boardId, long stackId, long cardId, String mandat, boolean pflicht) {
        stack(boardId, stackId, mandat);
        return cardRepository.findById(cardId)
                .filter(k -> k.getStack().getId() == stackId)
                .orElseThrow(() -> new NoSuchElementException("Karte " + cardId + " nicht auf Stapel " + stackId));
    }

    private BoardCard cardById(long cardId, String mandat) {
        BoardCard karte = cardRepository.findById(cardId)
                .orElseThrow(() -> new NoSuchElementException("Karte " + cardId + " nicht gefunden"));
        board(karte.getBoard().getId(), mandat, true);
        return karte;
    }

    private Date parseDueDate(String duedate) {
        if (duedate == null || duedate.isBlank()) {
            return null;
        }
        return Date.from(Instant.parse(duedate));
    }
}
