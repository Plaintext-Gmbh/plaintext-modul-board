/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

/**
 * Backing Bean der Kanban-Seite (Karte 2193). Phase 4 baut daraus die volle Ansicht
 * (Stapel-Spalten, Drag and Drop, Filter); bis dahin zeigt die Seite die Karten des
 * ersten Boards als Liste. Session-scoped wie die anderen Seiten-Beans (kein @ViewScoped).
 */
@Component("boardBean")
@Scope("session")
public class BoardBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private final transient BoardCardRepository cardRepository;
    private final transient BoardRepository boardRepository;

    @Getter
    private List<BoardCard> karten = List.of();

    public BoardBean(BoardCardRepository cardRepository, BoardRepository boardRepository) {
        this.cardRepository = cardRepository;
        this.boardRepository = boardRepository;
    }

    public void laden() {
        List<Board> boards = boardRepository.findAll();
        if (boards.isEmpty()) {
            karten = List.of();
            return;
        }
        karten = cardRepository.findByBoardId(boards.get(0).getId());
    }
}
