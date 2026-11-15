/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-Tests fuer {@link BoardMarkdownService}: Markdown-Rendering (GFM-Tabellen, Tasklisten)
 * und das IMMER laufende Sanitizing (Script-Tags, Event-Handler, javascript:-URLs fliegen raus).
 */
class BoardMarkdownServiceTest {

    private final BoardMarkdownService service = new BoardMarkdownService();

    @Test
    void leerGibtLeer() {
        assertThat(service.render(null)).isEmpty();
        assertThat(service.render("  ")).isEmpty();
    }

    @Test
    void fettUndUeberschrift() {
        String html = service.render("# Titel\n\n**fett** und *kursiv*");
        assertThat(html).contains("<h1>Titel</h1>").contains("<strong>fett</strong>").contains("<em>kursiv</em>");
    }

    @Test
    void gfmTabelleWirdGerendert() {
        String html = service.render("| a | b |\n|---|---|\n| 1 | 2 |");
        assertThat(html).contains("<table>").contains("<td>1</td>");
    }

    @Test
    void tasklistCheckboxBleibt() {
        String html = service.render("- [x] erledigt\n- [ ] offen");
        assertThat(html).contains("checked").contains("disabled").contains("type=\"checkbox\"");
    }

    @Test
    void scriptUndEventHandlerFliegenRaus() {
        String html = service.render("**hi** <script>alert(1)</script> <img src=x onerror=alert(1)> [x](javascript:alert(1))");
        assertThat(html).doesNotContain("<script").doesNotContain("onerror").doesNotContain("javascript:");
        assertThat(html).contains("<strong>hi</strong>");
    }
}
