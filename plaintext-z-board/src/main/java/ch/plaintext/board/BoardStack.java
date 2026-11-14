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
 * Ein Stapel (Spalte) eines Boards, sortierbar (Karte 2193). WIP-Limit je Stapel, beim
 * Plaintext-Backlog z. B. inprogress = 5; null = kein Limit.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class BoardStack extends SuperModel {

    @ManyToOne
    private Board board;

    private String title;

    /** Position des Stapels auf dem Board (0-basiert). */
    private int sortIndex;

    /** WIP-Limit (Karten im Stapel); null = unbegrenzt. */
    private Integer wipLimit;
}
