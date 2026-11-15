/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board.mcp;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.board.BoardDeckJson.BoardJson;
import ch.plaintext.board.BoardDeckJson.CardJson;
import ch.plaintext.board.BoardDeckJson.CommentJson;
import ch.plaintext.board.BoardDeckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link BoardMcpTools} — reine Mocks (kein DB-Boot), Stil wie ZeiterfassungMcpToolsTest.
 * Das Mandat kommt ausschliesslich aus {@link PlaintextSecurity} und wird an den Service weitergereicht.
 */
@ExtendWith(MockitoExtension.class)
class BoardMcpToolsTest {

    @Mock private BoardDeckService service;
    @Mock private PlaintextSecurity security;

    @InjectMocks private BoardMcpTools tools;

    @BeforeEach
    void setUp() {
        lenient().when(security.getMandat()).thenReturn("m1");
        lenient().when(security.getId()).thenReturn(42L);
        lenient().when(security.getUser()).thenReturn("kimi@example.ch");
    }

    // ---- board_list_boards / board_list_stacks ----

    @Test
    void listBoards_delegiertMitMandatAusSecurity() {
        BoardJson b = new BoardJson();
        b.id = 7;
        b.title = "Backlog";
        when(service.boards("m1")).thenReturn(List.of(b));

        List<BoardJson> boards = tools.listBoards();

        assertThat(boards).hasSize(1);
        assertThat(boards.get(0).id).isEqualTo(7L);
        verify(service).boards("m1");
    }

    @Test
    void listStacks_ohneBoardId_nutztEinzigesBoard() {
        BoardJson b = new BoardJson();
        b.id = 7;
        when(service.boards("m1")).thenReturn(List.of(b));
        when(service.stacks(7L, "m1")).thenReturn(List.of());

        tools.listStacks(null);

        verify(service).stacks(7L, "m1");
    }

    @Test
    void listStacks_ohneBoardId_beiMehrerenBoards_fehler() {
        BoardJson a = new BoardJson();
        a.id = 7;
        BoardJson b = new BoardJson();
        b.id = 8;
        when(service.boards("m1")).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> tools.listStacks(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boardId angeben");
    }

    @Test
    void listStacks_mitBoardId_nutztDieses() {
        when(service.stacks(8L, "m1")).thenReturn(List.of());

        tools.listStacks(8L);

        verify(service).stacks(8L, "m1");
        verify(service, never()).boards(any());
    }

    // ---- board_get_card ----

    @Test
    void getCard_liefertKarteUndKommentare() {
        CardJson karte = new CardJson();
        karte.id = 99;
        CommentJson kommentar = new CommentJson();
        kommentar.id = 5;
        when(service.card(99L, "m1")).thenReturn(karte);
        when(service.comments(99L, "m1")).thenReturn(List.of(kommentar));

        BoardMcpTools.CardInfo info = tools.getCard(99L);

        assertThat(info.karte().id).isEqualTo(99L);
        assertThat(info.kommentare()).hasSize(1);
    }

    @Test
    void getCard_ohneCardId_fehler() {
        assertThatThrownBy(() -> tools.getCard(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cardId");
    }

    // ---- board_create_card ----

    @Test
    void createCard_delegiertAnService() {
        CardJson angelegt = new CardJson();
        angelegt.id = 55;
        when(service.createCard(eq(3L), eq("Neue Karte"), isNull(), isNull(), isNull(), eq("m1")))
                .thenReturn(angelegt);

        CardJson res = tools.createCard(3L, "Neue Karte", null, null);

        assertThat(res.id).isEqualTo(55L);
        verify(service).createCard(3L, "Neue Karte", null, null, null, "m1");
    }

    @Test
    void createCard_leererTitel_fehler() {
        assertThatThrownBy(() -> tools.createCard(3L, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titel");
    }

    // ---- board_move_card ----

    @Test
    void moveCard_ohneReihenfolge_appendedAmEnde() {
        String res = tools.moveCard(10L, 4L, null);

        assertThat(res).contains("10").contains("4");
        verify(service).moveCard(10L, 4L, 999, "m1");
    }

    @Test
    void moveCard_mitReihenfolge() {
        tools.moveCard(10L, 4L, 0);

        verify(service).moveCard(10L, 4L, 0, "m1");
    }

    // ---- board_append_description ----

    @Test
    void appendDescription_delegiertAnService() {
        CardJson karte = new CardJson();
        karte.id = 10;
        when(service.appendDescription(10L, "Stand: CI gruen", "m1")).thenReturn(karte);

        CardJson res = tools.appendDescription(10L, " Stand: CI gruen ");

        assertThat(res.id).isEqualTo(10L);
        verify(service).appendDescription(10L, "Stand: CI gruen", "m1");
    }

    @Test
    void appendDescription_leererText_fehler() {
        assertThatThrownBy(() -> tools.appendDescription(10L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    // ---- board_add_comment ----

    @Test
    void addComment_setztAutorAusSecurity() {
        CommentJson kommentar = new CommentJson();
        kommentar.id = 12;
        when(service.addComment(eq(10L), eq("Erledigt"), eq("42"), eq("kimi@example.ch"), eq("m1")))
                .thenReturn(kommentar);

        CommentJson res = tools.addComment(10L, "Erledigt");

        assertThat(res.id).isEqualTo(12L);
        verify(service).addComment(10L, "Erledigt", "42", "kimi@example.ch", "m1");
    }

    // ---- board_set_label ----

    @Test
    void setLabel_delegiertAnService() {
        CardJson karte = new CardJson();
        karte.id = 10;
        when(service.setLabel(10L, "kimi", null, "m1")).thenReturn(karte);

        CardJson res = tools.setLabel(10L, "kimi", null);

        assertThat(res.id).isEqualTo(10L);
        verify(service).setLabel(10L, "kimi", null, "m1");
    }

    @Test
    void setLabel_ohneCardId_fehler() {
        assertThatThrownBy(() -> tools.setLabel(null, "kimi", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cardId");
    }
}
