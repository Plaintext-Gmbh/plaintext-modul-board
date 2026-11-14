/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardLabelRepository extends JpaRepository<BoardLabel, Long> {

    List<BoardLabel> findByBoardId(Long boardId);
}
