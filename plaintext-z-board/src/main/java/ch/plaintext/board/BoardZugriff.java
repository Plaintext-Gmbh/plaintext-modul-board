/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Einzelzugriffe per Id auf Board-Entitaeten — Karte 1533 (AR1): findById liegt NUR hier (die
 * Klasse endet auf {@code Zugriff} und ist vom Archtest ausgenommen), mit Sichtbarkeitspruefung
 * auf Mandat (Board) bzw. Board-Zugehoerigkeit (Stapel/Karte). Alles andere holt Datensaetze
 * ueber diese Klasse, nicht direkt aus den Repositories.
 */
@Component
@RequiredArgsConstructor
public class BoardZugriff {

    private final BoardRepository boardRepository;
    private final BoardStackRepository stackRepository;
    private final BoardCardRepository cardRepository;
    private final BoardLabelRepository labelRepository;
    private final BoardCommentRepository commentRepository;

    /** Board nur sichtbar im eigenen Mandat. */
    public Board board(long boardId, String mandat) {
        return boardRepository.findById(boardId)
                .filter(b -> mandat.equals(b.getMandat()))
                .orElseThrow(() -> new NoSuchElementException("Board " + boardId + " nicht im Mandat " + mandat));
    }

    public BoardStack stack(long stackId) {
        return stackRepository.findById(stackId)
                .orElseThrow(() -> new NoSuchElementException("Stapel " + stackId + " nicht gefunden"));
    }

    /** Stapel, aber nur auf dem genannten Board. */
    public BoardStack stack(long boardId, long stackId) {
        BoardStack s = stack(stackId);
        if (!s.getBoard().getId().equals(boardId)) {
            throw new NoSuchElementException("Stapel " + stackId + " nicht auf Board " + boardId);
        }
        return s;
    }

    public BoardCard card(long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new NoSuchElementException("Karte " + cardId + " nicht gefunden"));
    }

    /** Karte, aber nur auf dem genannten Board (Deck ignoriert die Stack-Id im Pfad — Schutz = Board). */
    public BoardCard cardAufBoard(long boardId, long cardId) {
        BoardCard karte = card(cardId);
        if (!karte.getBoard().getId().equals(boardId)) {
            throw new NoSuchElementException("Karte " + cardId + " nicht auf Board " + boardId);
        }
        return karte;
    }

    public BoardLabel label(long labelId) {
        return labelRepository.findById(labelId)
                .orElseThrow(() -> new NoSuchElementException("Label " + labelId + " nicht gefunden"));
    }

    public BoardComment comment(long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Kommentar " + commentId + " nicht gefunden"));
    }
}
