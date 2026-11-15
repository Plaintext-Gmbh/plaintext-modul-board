/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board.menu;

import ch.plaintext.boot.menu.MenuAnnotation;

/**
 * Kanban-Board als eigener Menuepunkt; die @MenuAnnotation traegt zugleich die
 * Zugriffsregel fuer board.xhtml (PageGuardZuordnungTest).
 */
@MenuAnnotation(
        title = "Board",
        link = "board.html",
        order = 10,
        icon = "pi pi-th-large",
        roles = {"USER", "ADMIN", "ROOT"}
)
public class BoardMenu {
}
