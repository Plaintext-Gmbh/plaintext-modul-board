/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.framework.SuperModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Ein Kommentar an einer Karte (Karte 2193). Autor als Freitext-Id und Anzeigename, weil die
 * schreibenden Agenten per API-Token kommen — die Aufloesung bleibt dem Board-Aufrufer.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class BoardComment extends SuperModel {

    @ManyToOne
    private BoardCard card;

    @Column(length = 100000)
    private String message;

    private String actorId;

    private String actorDisplayName;
}
