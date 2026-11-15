/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import ch.plaintext.modules.ModuleDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Meldet das Board-Modul an die zentrale Modul-Verwaltung (Root | Module), Muster WikiModuleDescriptor. */
@Component
public class BoardModuleDescriptor implements ModuleDescriptor {

    @Override
    public String moduleId() {
        return "board";
    }

    @Override
    public String displayName() {
        return "Board";
    }

    @Override
    public List<Class<?>> entities() {
        return List.of(Board.class, BoardStack.class, BoardCard.class, BoardLabel.class,
                BoardComment.class);
    }
}
