/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-Formate der Deck-kompatiblen REST-API (Karte 2193): Feldnamen und Struktur wie die
 * Nextcloud-Deck-API v1.0, damit {@code ~/scripts/deck} nur Basis-URL und Auth tauscht.
 * Oeffentliche Felder — Jackson serialisiert sie direkt, ohne Boilerplate.
 */
public final class BoardDeckJson {

    private BoardDeckJson() {
    }

    /** Ein Board ({@code GET /boards}). */
    public static class BoardJson {
        public long id;
        public String title;

        public static BoardJson of(Board b) {
            BoardJson j = new BoardJson();
            j.id = b.getId();
            j.title = b.getTitle();
            return j;
        }
    }

    /** Ein Label ({@code labels} an der Karte). */
    public static class LabelJson {
        public long id;
        public String title;
        public String color;

        public static LabelJson of(BoardLabel l) {
            LabelJson j = new LabelJson();
            j.id = l.getId();
            j.title = l.getTitle();
            j.color = l.getColor();
            return j;
        }
    }

    /** Eine Karte. {@code order} entspricht der Deck-Reihenfolge/Prioritaet. */
    public static class CardJson {
        public long id;
        public String title;
        public String description;
        public long stackId;
        public int order;
        public String duedate;
        public boolean archived;
        public List<LabelJson> labels = new ArrayList<>();

        public static CardJson of(BoardCard c) {
            CardJson j = new CardJson();
            j.id = c.getId();
            j.title = c.getTitle();
            j.description = c.getDescription() == null ? "" : c.getDescription();
            j.stackId = c.getStack().getId();
            j.order = c.getSortIndex();
            j.duedate = c.getDueDate() == null ? null : c.getDueDate().toInstant().toString();
            j.archived = c.isArchived();
            c.getLabels().forEach(l -> j.labels.add(LabelJson.of(l)));
            return j;
        }
    }

    /** Ein Stapel; {@code cards} liegt eingebettet wie im Deck ({@code GET /boards/{bid}/stacks}). */
    public static class StackJson {
        public long id;
        public String title;
        public int order;
        public List<CardJson> cards = new ArrayList<>();

        public static StackJson of(BoardStack s, List<BoardCard> karten) {
            StackJson j = new StackJson();
            j.id = s.getId();
            j.title = s.getTitle();
            j.order = s.getSortIndex();
            karten.forEach(k -> j.cards.add(CardJson.of(k)));
            return j;
        }
    }

    /** Ein Kommentar ({@code actorId}/{@code actorDisplayName}/{@code creationDateTime} wie im Deck). */
    public static class CommentJson {
        public long id;
        public String message;
        public String actorId;
        public String actorDisplayName;
        public String creationDateTime;

        public static CommentJson of(BoardComment c) {
            CommentJson j = new CommentJson();
            j.id = c.getId();
            j.message = c.getMessage();
            j.actorId = c.getActorId();
            j.actorDisplayName = c.getActorDisplayName();
            j.creationDateTime = c.getCreatedDate() == null ? Instant.EPOCH.toString()
                    : c.getCreatedDate().toInstant().toString();
            return j;
        }
    }
}
