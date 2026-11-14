/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.framework.SuperModel;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Eine Karte (Karte 2193): Titel, Beschreibung als Markdown, Prioritaet/Reihenfolge, Faelligkeit,
 * Labels, Bearbeiter, Archiv-Flag. Die ID ist bei der Migration aus dem Nextcloud-Deck frei
 * setzbar und bleibt danach bestehen — Agenten und Memories verweisen auf Karten-Nummern.
 * <p>
 * {@link #sortIndex} entspricht der Deck-{@code order}: kleinere Zahl = weiter oben.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class BoardCard extends SuperModel {

    @ManyToOne
    private Board board;

    @ManyToOne
    private BoardStack stack;

    private String title;

    /** Markdown; Laenge wie Deck-Beschreibungen (Karte 785 'append' haengt an). */
    @Column(length = 100000)
    private String description;

    /** Reihenfolge/Prioritaet im Stapel (Deck 'order'). */
    private int sortIndex;

    private Date dueDate;

    private boolean archived;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<BoardLabel> labels = new LinkedHashSet<>();

    /** Zugewiesene Benutzer (Benutzer-Ids der App). */
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Long> assignedUserIds = new LinkedHashSet<>();
}
