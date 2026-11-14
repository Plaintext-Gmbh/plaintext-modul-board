/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.framework.SuperModel;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Ein Kanban-Board eines Mandanten (Karte 2193). Beim Plaintext-Backlog gibt es genau eines;
 * das Modell traegt trotzdem das Board, weil die Deck-API pfadweise daran haengt.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class Board extends SuperModel {

    private String title;
}
