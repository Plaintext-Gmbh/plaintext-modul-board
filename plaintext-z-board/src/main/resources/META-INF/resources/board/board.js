/*
 * Drag and Drop des Kanban-Boards (board.xhtml, Karte 2193 Phase 4). Reines HTML5-DnD:
 * Karten tragen data-pt-karte, Spalten data-pt-stack; der Drop ruft den p:remoteCommand
 * «verschieben» mit karte/ziel auf. Anbindung ueber Klassen/Attribute statt fester IDs,
 * damit mehrere Spalten und zwei Boards auf einer Seite nebeneinander laufen.
 * Mobil (Touch) feuert kein DnD — dort laeuft das Verschieben ueber die Buttons im Kartendetail.
 */
(function () {
    'use strict';

    document.addEventListener('dragstart', function (e) {
        var karte = e.target && e.target.closest ? e.target.closest('.pt-board-karte') : null;
        if (!karte || !e.dataTransfer) {
            return;
        }
        e.dataTransfer.setData('text/plain', karte.getAttribute('data-pt-karte'));
        e.dataTransfer.effectAllowed = 'move';
        karte.classList.add('pt-board-zieht');
    });

    document.addEventListener('dragend', function (e) {
        var karte = e.target && e.target.closest ? e.target.closest('.pt-board-karte') : null;
        if (karte) {
            karte.classList.remove('pt-board-zieht');
        }
        raeumen();
    });

    document.addEventListener('dragover', function (e) {
        var spalte = e.target && e.target.closest ? e.target.closest('.pt-board-spalte') : null;
        if (!spalte || !e.dataTransfer) {
            return;
        }
        // Ohne preventDefault feuert kein drop (HTML5-Regel).
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        if (!spalte.classList.contains('pt-board-ziel')) {
            raeumen();
            spalte.classList.add('pt-board-ziel');
        }
    });

    document.addEventListener('drop', function (e) {
        var spalte = e.target && e.target.closest ? e.target.closest('.pt-board-spalte') : null;
        if (!spalte || !e.dataTransfer) {
            return;
        }
        e.preventDefault();
        var karte = e.dataTransfer.getData('text/plain');
        var ziel = spalte.getAttribute('data-pt-stack');
        raeumen();
        if (karte && ziel && typeof window.verschieben === 'function') {
            window.verschieben([{name: 'karte', value: karte}, {name: 'ziel', value: ziel}]);
        }
    });

    function raeumen() {
        var markiert = document.querySelectorAll('.pt-board-spalte.pt-board-ziel');
        for (var i = 0; i < markiert.length; i++) {
            markiert[i].classList.remove('pt-board-ziel');
        }
    }
})();
