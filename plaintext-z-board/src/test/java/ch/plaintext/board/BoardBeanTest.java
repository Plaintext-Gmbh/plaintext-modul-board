/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.board.BoardDeckJson.BoardJson;
import ch.plaintext.board.BoardDeckJson.CardJson;
import ch.plaintext.board.BoardDeckJson.LabelJson;
import ch.plaintext.board.BoardDeckJson.StackJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer {@link BoardBean} (Kanban, Karte 2193 Phase 4): Filter, WIP-Zaehlung auf
 * ungefiltertem Bestand, Verschieben, Kartendetail und Neukarten — reine Mocks.
 */
@ExtendWith(MockitoExtension.class)
class BoardBeanTest {

    @Mock private BoardDeckService service;
    @Mock private BoardLabelRepository labelRepository;
    @Mock private BoardMarkdownService markdown;
    @Mock private PlaintextSecurity security;

    @InjectMocks private BoardBean bean;

    private StackJson vorrat;
    private StackJson inprogress;

    @BeforeEach
    void setUp() {
        lenient().when(security.getMandat()).thenReturn("m1");
        lenient().when(security.getId()).thenReturn(7L);
        lenient().when(security.getUser()).thenReturn("kimi@example.test");

        BoardJson board = new BoardJson();
        board.id = 1;
        board.title = "Backlog";
        lenient().when(service.boards("m1")).thenReturn(List.of(board));
        lenient().when(service.board(1L, "m1")).thenReturn(board);

        vorrat = stapel(1, "vorrat", null, karte(10, "Alpha", 1), karte(11, "Beta", 1));
        inprogress = stapel(2, "inprogress", 2, karte(20, "Gamma mit Label", 2), karte(21, "Delta", 2));
        LabelJson label = new LabelJson();
        label.id = 5;
        label.title = "kimi";
        inprogress.cards.get(0).labels.add(label);
        lenient().when(service.stacks(1L, "m1")).thenReturn(List.of(vorrat, inprogress));
        lenient().when(service.cards(eq(1L), anyLong(), eq("m1")))
                .thenAnswer(inv -> inv.getArgument(1).equals(1L) ? vorrat.cards : inprogress.cards);
        lenient().when(labelRepository.findByBoardId(1L)).thenReturn(List.of());
    }

    private static StackJson stapel(long id, String titel, Integer wip, CardJson... karten) {
        StackJson s = new StackJson();
        s.id = id;
        s.title = titel;
        s.wipLimit = wip;
        s.cards = new java.util.ArrayList<>(List.of(karten));
        return s;
    }

    private static CardJson karte(long id, String titel, long stackId) {
        CardJson k = new CardJson();
        k.id = id;
        k.title = titel;
        k.stackId = stackId;
        k.description = "";
        return k;
    }

    @Test
    void ladenMitEinemBoard_loestBoardIdAuf() {
        bean.laden();
        assertThat(bean.getBoardTitel()).isEqualTo("Backlog");
        assertThat(bean.getStapel()).hasSize(2);
        assertThat(bean.getStapel().get(0).cards).hasSize(2);
    }

    @Test
    void ohneBoard_bleibtLeer() {
        when(service.boards("m1")).thenReturn(List.of());
        bean.laden();
        assertThat(bean.getBoardTitel()).isNull();
        assertThat(bean.getStapel()).isEmpty();
    }

    @Test
    void filterText_filtertTitelUndBeschreibung() {
        bean.setFilterText("gamma");
        bean.laden();
        assertThat(bean.getStapel().get(1).cards).extracting(k -> k.id).containsExactly(20L);
        assertThat(bean.getStapel().get(0).cards).isEmpty();
    }

    @Test
    void filterLabel_zeigtNurKartenMitLabel() {
        bean.setFilterLabelId(5L);
        bean.laden();
        assertThat(bean.getStapel().get(1).cards).extracting(k -> k.id).containsExactly(20L);
    }

    @Test
    void wipPruefungNutztUngefiltertenBestand() {
        bean.setFilterText("nix-matcht");
        bean.laden();
        // sichtbar sind null Karten, trotzdem zaehlt der volle Bestand (2 > wipLimit 2? nein)
        assertThat(bean.wipUeberschritten(inprogress)).isFalse();
        inprogress.cards.add(karte(22, "Epsilon", 2));
        bean.laden();
        assertThat(bean.wipUeberschritten(inprogress)).isTrue();
    }

    @Test
    void verschieben_setztEndpositionUndLaedtNeu() {
        bean.laden();
        bean.verschieben(10L, 2L);
        // Endposition = aktuelle Zielanzahl; die Datenbewegung selbst macht der Service.
        verify(service).moveCard(10L, 2L, 2, "m1");
        verify(service, org.mockito.Mockito.atLeastOnce()).stacks(1L, "m1");
    }

    @Test
    void detailOeffnen_befuelltFelderUndRendertMarkdown() {
        when(service.card(20L, "m1")).thenReturn(inprogress.cards.get(0));
        when(service.comments(20L, "m1")).thenReturn(List.of());
        when(markdown.render(anyString())).thenReturn("<p>x</p>");
        bean.laden();
        bean.detailOeffnen(20L);
        assertThat(bean.getDetailKarteId()).isEqualTo(20L);
        assertThat(bean.getDetailTitel()).isEqualTo("Gamma mit Label");
        assertThat(bean.getDetailHtml()).isEqualTo("<p>x</p>");
        assertThat(bean.getDetailStackId()).isEqualTo(2L);
    }

    @Test
    void kommentarSpeichern_leererTextWirdIgnoriert() {
        bean.setNeuerKommentar("  ");
        bean.kommentarSpeichern();
        verify(service, never()).addComment(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void kommentarSpeichern_delegiertMitAktuellemBenutzer() {
        when(service.card(20L, "m1")).thenReturn(inprogress.cards.get(0));
        when(service.comments(20L, "m1")).thenReturn(List.of());
        bean.laden();
        bean.detailOeffnen(20L);
        bean.setNeuerKommentar(" Erledigt ");
        bean.kommentarSpeichern();
        verify(service).addComment(eq(20L), eq("Erledigt"), eq("7"), eq("kimi@example.test"), eq("m1"));
    }

    @Test
    void karteAnlegen_leererTitelWirdIgnoriert() {
        bean.karteAnlegen(1L);
        verify(service, never()).createCard(anyLong(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void karteAnlegen_delegiertUndLaedtNeu() {
        bean.getNeuerTitel().put(1L, " Neue Karte ");
        bean.karteAnlegen(1L);
        verify(service).createCard(1L, "Neue Karte", null, null, null, "m1");
        assertThat(bean.getNeuerTitel()).isEmpty();
    }
}
