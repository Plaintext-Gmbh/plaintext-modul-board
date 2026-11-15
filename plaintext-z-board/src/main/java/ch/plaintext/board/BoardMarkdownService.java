/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rendert Karten-Beschreibungen (Markdown, aus dem Nextcloud-Deck uebernommen) zu sicherem HTML
 * fuer die Kartendetail-Ansicht. Muster WikiMarkdownRenderer (gleiche Pipeline):
 * flexmark (GFM-Tabellen + Tasklisten) -> jsoup-Sanitizing (relaxed + Tasklisten-Checkboxen),
 * das IMMER laeuft — entfernt Script-Tags, Event-Handler und javascript:-URLs.
 */
@Service
public class BoardMarkdownService {

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Safelist safelist;

    public BoardMarkdownService() {
        MutableDataSet optionen = new MutableDataSet();
        optionen.set(Parser.EXTENSIONS, List.of(TablesExtension.create(), TaskListExtension.create()));
        this.parser = Parser.builder(optionen).build();
        this.renderer = HtmlRenderer.builder(optionen).build();
        this.safelist = Safelist.relaxed()
                .addAttributes(":all", "class")
                .addAttributes("input", "checked", "disabled", "type");
    }

    /** Markdown → sicheres HTML; leer/null → leerer String. */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String roh = renderer.render(parser.parse(markdown));
        return Jsoup.clean(roh, "", safelist,
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml));
    }
}
