# plaintext-modul-board

Kanban-Board für Plaintext-Anwendungen (Karte 2193): Boards, sortierbare Stapel mit
WIP-Limit, Karten (Titel, Markdown-Beschreibung, Reihenfolge/Priorität, Fälligkeit,
Labels, Bearbeiter), Kommentare. Ersetzt das Nextcloud-Deck des «Plaintext-Backlog» —
die Karten-IDs aus dem Deck bleiben bei der Migration gleich.

REST-API deck-kompatibel: `~/scripts/deck` (Backend-Schalter `backend=nextcloud|app`)
spricht dieselben Endpunkte gegen die App; MCP-Werkzeuge (`board_*`) steuern das
Board natuerlichsprachlich — SCOPE_READ zum Lesen, SCOPE_WRITE zum Schreiben.

Offen, MPL-2.0.

## Einbau in eine Plaintext-App

1. **Abhängigkeit** (Version siehe Releases):

   ```xml
   <dependency>
       <groupId>ch.plaintext</groupId>
       <artifactId>plaintext-modul-board</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```

2. **Fertig.** Das Modul meldet sich selbst (`BoardAutoConfiguration`,
   `META-INF/spring/…AutoConfiguration.imports`): REST-API unter `/api/deck/v1`,
   MCP-Werkzeuge (`board_list_boards`, `board_list_stacks`, `board_get_card`,
   `board_create_card`, `board_move_card`, `board_append_description`,
   `board_add_comment`, `board_set_label`), Tabellen per Flyway (`db/migration`);
   die Kanban-Seite folgt.

3. **Zugriff für Skripte/Agenten:** API-Token (ADMIN-Scope) als
   `Authorization: Bearer <token>`, wie die anderen MCP-/REST-Werkzeuge der App.

## Entwicklung

```bash
mvn verify   # braucht Erreichbarkeit von maven.plaintext.ch (LAN) für den root-Parent
```

Der Parent ist `plaintext-root-parent`; alle root-Teile sind `provided` — die
Zielanwendung bringt sie mit. Die Modul-Version ist unabhängig von der root-Version.
