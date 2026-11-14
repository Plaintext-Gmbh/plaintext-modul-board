/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.framework.SuperModel;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Ein Board-Label (Karte 2193): Rollen (kimi, worker, ...), Status (Zu überprüfen, Blockiert,
 * Analyse, Rückfrage) und freie Markierungen — farblich wie im Nextcloud-Deck.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class BoardLabel extends SuperModel {

    @ManyToOne
    private Board board;

    private String title;

    private String color;
}
