package ch.plaintext.board;

import ch.plaintext.boot.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 2193, Phase 1a: Domain haelt, was das Deck braucht — Labels an der Karte, Kommentare mit
 * Autor, Reihenfolge je Stapel, Stapel-Sortierung. (Feste Karten-IDs kommen mit dem Importer,
 * Phase 2: JPA-IDENTITY laesst keine vorgegebenen Ids zu, der Import laeuft ueber native INSERTs.)
 */
class BoardDomainTest extends BaseIntegrationTest {

    @Autowired
    BoardRepository boardRepository;
    @Autowired
    BoardStackRepository stackRepository;
    @Autowired
    BoardCardRepository cardRepository;
    @Autowired
    BoardLabelRepository labelRepository;
    @Autowired
    BoardCommentRepository commentRepository;

    @Test
    void karteMitLabelsKommentarUndReihenfolge() {
        Board board = new Board();
        board.setTitle("Plaintext-Backlog");
        board.setMandat("TEST");
        board = boardRepository.save(board);

        BoardStack todo = new BoardStack();
        todo.setBoard(board);
        todo.setTitle("todo");
        todo.setSortIndex(0);
        todo.setWipLimit(5);
        todo = stackRepository.save(todo);

        BoardLabel kimi = new BoardLabel();
        kimi.setBoard(board);
        kimi.setTitle("kimi");
        kimi.setColor("ffcc00");
        kimi = labelRepository.save(kimi);

        BoardCard card = new BoardCard();
        card.setBoard(board);
        card.setStack(todo);
        card.setTitle("board-modul-kanban-ersetzt-nextcloud-deck");
        card.setDescription("## Auftrag Daniel\n\nText");
        card.setSortIndex(400);
        card.getLabels().add(kimi);
        card.getAssignedUserIds().add(1L);
        card = cardRepository.save(card);
        cardRepository.flush();

        assertNotNull(card.getId());

        BoardComment comment = new BoardComment();
        comment.setCard(card);
        comment.setMessage("Hallo Board");
        comment.setActorId("kimi1");
        comment.setActorDisplayName("kimi1");
        comment = commentRepository.save(comment);

        List<BoardComment> alle = commentRepository.findByCardIdOrderByCreatedDate(card.getId());
        assertEquals(1, alle.size());
        assertEquals(comment.getId(), alle.get(0).getId());
        assertEquals("Hallo Board", alle.get(0).getMessage());
        assertEquals("kimi1", alle.get(0).getActorId());

        BoardCard frisch = cardRepository.findById(card.getId()).orElseThrow();
        assertEquals("board-modul-kanban-ersetzt-nextcloud-deck", frisch.getTitle());
        assertEquals(todo.getId(), frisch.getStack().getId());
        assertEquals(400, frisch.getSortIndex());
        assertEquals(1, frisch.getLabels().size());
        assertTrue(frisch.getAssignedUserIds().contains(1L));

        List<BoardCard> stapel = cardRepository.findByStackIdOrderBySortIndex(todo.getId());
        assertEquals(1, stapel.size());

        BoardStack geladen = stackRepository.findById(todo.getId()).orElseThrow();
        assertEquals(Integer.valueOf(5), geladen.getWipLimit());
    }
}
