#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Migration Nextcloud-Deck -> plaintext-modul-board (Karte 2193, Phase 2).

Liest ein Nextcloud-Deck-Board vollstaendig aus (Stapel, Karten inkl. Archiv-Flag,
Labels, Kommentare) und importiert es ueber die Deck-kompatible REST-API der App
(/api/deck/v1). Die Karten-IDs bleiben 1:1 erhalten — Agenten und Memories verweisen
auf Karten-Nummern. Der Import ist idempotent (SQL-Upsert), Probelauf und Nachimport
erzeugen keine Dubletten.

Aufruf:
    python3 deck-migration.py export --backlog ~/.backlog --ziel export.json
    python3 deck-migration.py import --ziel export.json --app-url https://app... \
        --app-token $TOKEN [--board-titel "Plaintext-Backlog"]
    python3 deck-migration.py diff --ziel export.json --app-url https://app... --app-token $TOKEN

Nur Standardbibliothek — laeuft auf zorin01/zorin02 ohne Installation.
"""

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request

API_PFAD = "/index.php/apps/deck/api/v1.0"
OCS_PFAD = "/ocs/v2.php/apps/deck/api/v1.0"


# ── HTTP-Helfer ──────────────────────────────────────────────

def http_json(method, url, token=None, basic=None, body=None, timeout=60):
    req = urllib.request.Request(url, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("OCS-APIRequest", "true")  # Nextcloud-OCS verlangt den Header (sonst 412)
    req.add_header("User-Agent", "curl/8.0")  # Cloudflare auf home.plaintext.ch laesst keinen Python-urllib-UA durch
    if body is not None:
        req.add_header("Content-Type", "application/json")
        req.data = json.dumps(body).encode("utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    elif basic:
        import base64
        req.add_header("Authorization", "Basic " + base64.b64encode(basic.encode()).decode())
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            roh = resp.read().decode("utf-8")
            return resp.status, (json.loads(roh) if roh else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:500]


# ── Export aus dem Nextcloud-Deck ────────────────────────────

def deck_export(conf_pfad, board_id=None):
    conf = {}
    with open(os.path.expanduser(conf_pfad), encoding="utf-8") as f:
        for zeile in f:
            if "=" in zeile and not zeile.startswith("#"):
                k, w = zeile.strip().split("=", 1)
                conf[k] = w
    host = conf.get("host")
    basic = conf.get("user", "") + ":" + conf.get("token", "")
    if not host or not basic:
        sys.exit("~/.backlog braucht host=, user=, token=")
    board_id = board_id or int(conf.get("board", "0"))
    if not board_id:
        sys.exit("Board-Id fehlt (CONF board=)")

    api = host + API_PFAD
    ocs = host + OCS_PFAD
    status, stacks = http_json("GET", f"{api}/boards/{board_id}/stacks", basic=basic)
    if status != 200:
        sys.exit(f"Deck-Export stacks: HTTP {status}: {stacks}")

    export = {"boardId": board_id, "titel": None, "stapel": [], "karten": []}
    status, board = http_json("GET", f"{api}/boards/{board_id}", basic=basic)
    if status == 200 and isinstance(board, dict):
        export["titel"] = board.get("title")

    for stapel in sorted(stacks or [], key=lambda s: s.get("order", 0)):
        export["stapel"].append({"id": stapel.get("id"), "titel": stapel.get("title"),
                                 "order": stapel.get("order", 0)})
        for karte in sorted(stapel.get("cards") or [], key=lambda c: (c.get("order", 0), c.get("id", 0))):
            cid = karte["id"]
            status, kommentare = http_json("GET", f"{ocs}/cards/{cid}/comments", basic=basic)
            if status != 200:
                print(f"  WARN Kommentare Karte {cid}: HTTP {status}", file=sys.stderr)
                kommentare = []
            elif isinstance(kommentare, dict) and "ocs" in kommentare:
                kommentare = kommentare["ocs"].get("data") or []
            export["karten"].append({
                "id": cid,
                "stapelTitel": stapel.get("title"),
                "titel": karte.get("title"),
                "beschreibung": karte.get("description") or "",
                "order": karte.get("order", 0),
                "faellig": karte.get("duedate"),
                "archiviert": bool(karte.get("archived")),
                "labels": [{"titel": l.get("title"), "farbe": l.get("color")} for l in karte.get("labels") or []],
                "kommentare": [
                    {"nachricht": c.get("message") or "",
                     "autor": (c.get("actorDisplayName") or c.get("actorId") or "?"),
                     "zeit": (c.get("creationDateTime") or "")[:19] + ("Z" if c.get("creationDateTime") else "")}
                    for c in (kommentare or [])
                ],
            })
    return export


# ── Import in die App ────────────────────────────────────────

def app_find_board(app_url, app_token, titel):
    status, boards = http_json("GET", app_url + "/api/deck/v1/boards", token=app_token)
    if status != 200:
        sys.exit(f"App boards: HTTP {status}: {boards}")
    for b in boards:
        if b.get("title") == titel:
            return b["id"]
    return None


def app_stapel_id(app_url, app_token, board_id, titel):
    status, stapel = http_json("GET", app_url + f"/api/deck/v1/boards/{board_id}/stacks", token=app_token)
    if status != 200:
        sys.exit(f"App stacks: HTTP {status}: {stapel}")
    for s in stapel:
        if s.get("title") == titel:
            return s["id"]
    status, neu = http_json("POST", app_url + f"/api/deck/v1/boards/{board_id}/stacks",
                            token=app_token, body={"title": titel})
    if status not in (200, 201):
        sys.exit(f"App stack anlegen ({titel}): HTTP {status}: {neu}")
    return neu["id"]


def iso_zeit(zeit):
    """Deck-Zeitstempel (2026-11-14T18:05:00) nach ISO-Instant normalisieren."""
    if not zeit:
        return None
    z = zeit.strip()
    if z.endswith("Z"):
        return z
    if len(z) == 19 and "T" in z:
        return z + "Z"
    return z


def iso_due(wert):
    if not wert:
        return None
    z = wert.strip().replace("+00:00", "Z")
    if z.endswith("Z"):
        return z
    return z


def app_import(app_url, app_token, export, board_titel):
    titel = board_titel or export.get("titel") or "Plaintext-Backlog"
    board_id = app_find_board(app_url, app_token, titel)
    if board_id is None:
        status, board = http_json("POST", app_url + "/api/deck/v1/boards",
                                  token=app_token, body={"title": titel})
        if status not in (200, 201):
            sys.exit(f"App board anlegen: HTTP {status}: {board}")
        board_id = board["id"]
        print(f"Board '{titel}' angelegt: id {board_id}")
    else:
        print(f"Board '{titel}' vorhanden: id {board_id}")

    stapel_ids = {}
    for stapel in export["stapel"]:
        stapel_ids[stapel["titel"]] = app_stapel_id(app_url, app_token, board_id, stapel["titel"])

    ok, fehler = 0, 0
    for karte in export["karten"]:
        sid = stapel_ids.get(karte["stapelTitel"])
        if sid is None:
            print(f"  FEHLER Karte {karte['id']}: unbekannter Stapel {karte['stapelTitel']}")
            fehler += 1
            continue
        payload = {
            "id": karte["id"],
            "stackId": sid,
            "title": karte["titel"],
            "description": karte["beschreibung"],
            "order": karte["order"],
            "duedate": iso_due(karte.get("faellig")),
            "archived": karte["archiviert"],
            "labels": [{"title": l["titel"], "color": l.get("farbe")} for l in karte["labels"]],
            "comments": [
                {"message": c["nachricht"], "actorId": c.get("autor"), "actorDisplayName": c.get("autor"),
                 "createdDate": iso_zeit(c.get("zeit"))}
                for c in karte["kommentare"]
            ],
        }
        status, antwort = http_json("POST", app_url + f"/api/deck/v1/boards/{board_id}/import/cards",
                                    token=app_token, body=payload)
        if status == 200:
            ok += 1
        else:
            print(f"  FEHLER Karte {karte['id']}: HTTP {status}: {antwort}")
            fehler += 1
    print(f"Import: {ok} Karten importiert, {fehler} Fehler, Board {board_id}")
    return fehler == 0


# ── Diff (App-Stand gegen Export-Schnappschuss) ──────────────

def karten_signatur(karte, kommentare):
    h = hashlib.sha256()
    h.update(str(karte.get("titel") or karte.get("title") or "").encode())
    h.update(str(karte.get("beschreibung") if "beschreibung" in karte else karte.get("description") or "").encode())
    h.update(str(karte.get("order", 0)).encode())
    labels = sorted((l.get("titel") or l.get("title") or "") for l in karte.get("labels") or [])
    h.update("|".join(labels).encode())
    for c in kommentare or []:
        h.update(str(c.get("nachricht") or c.get("message") or "").encode())
        h.update(str(c.get("zeit") or c.get("createdDateTime") or "").encode())
    return h.hexdigest()[:16]


def app_zustand(app_url, app_token, board_id):
    status, stapel = http_json("GET", app_url + f"/api/deck/v1/boards/{board_id}/stacks", token=app_token)
    if status != 200:
        sys.exit(f"App stacks: HTTP {status}")
    zustand = {}
    for s in stapel:
        for k in s.get("cards") or []:
            status, kommentare = http_json("GET", app_url + f"/api/deck/v1/cards/{k['id']}/comments",
                                           token=app_token)
            zustand[k["id"]] = karten_signatur(k, kommentare if status == 200 else [])
    return zustand


def app_diff(app_url, app_token, export):
    titel = export.get("titel") or "Plaintext-Backlog"
    board_id = app_find_board(app_url, app_token, titel)
    if board_id is None:
        sys.exit(f"Board '{titel}' in der App nicht gefunden")
    ist = app_zustand(app_url, app_token, board_id)

    # Export-Signaturen: Kommentare liegen an der Karte (kein App-Aufruf noetig)
    export_sign = {}
    for k in export["karten"]:
        export_sign[k["id"]] = karten_signatur(
            {"titel": k["titel"], "beschreibung": k["beschreibung"], "order": k["order"],
             "labels": k["labels"]},
            [{"nachricht": c["nachricht"], "zeit": c.get("zeit")} for c in k["kommentare"]])

    fehlend = [cid for cid in export_sign if cid not in ist]
    extra = [cid for cid in ist if cid not in export_sign]
    anders = [cid for cid in export_sign if cid in ist and ist[cid] != export_sign[cid]]
    print(f"Diff gegen App-Board {board_id} ('{titel}'):")
    print(f"  Export: {len(export_sign)} Karten, App: {len(ist)} Karten")
    print(f"  fehlend in App: {len(fehlend)}  zusaetzlich in App: {len(extra)}  Inhalt anders: {len(anders)}")
    for cid in fehlend[:20]:
        print(f"    fehlend: {cid}")
    for cid in anders[:20]:
        print(f"    anders:  {cid} (export {export_sign[cid]} != app {ist[cid]})")
    return not fehlend and not anders


# ── main ─────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="befehl", required=True)

    p_export = sub.add_parser("export", help="Nextcloud-Deck auslesen")
    p_export.add_argument("--backlog", default="~/.backlog", help="CONF-Datei mit host/user/token")
    p_export.add_argument("--board", type=int, default=None, help="Board-Id (Standard: CONF board=)")
    p_export.add_argument("--ziel", required=True, help="Export-JSON")

    p_import = sub.add_parser("import", help="Export in die App importieren")
    p_import.add_argument("--ziel", required=True, help="Export-JSON")
    p_import.add_argument("--app-url", required=True)
    p_import.add_argument("--app-token", required=True)
    p_import.add_argument("--board-titel", default=None)

    p_diff = sub.add_parser("diff", help="App-Stand gegen Export pruefen")
    p_diff.add_argument("--ziel", required=True, help="Export-JSON")
    p_diff.add_argument("--app-url", required=True)
    p_diff.add_argument("--app-token", required=True)

    args = parser.parse_args()
    if args.befehl == "export":
        export = deck_export(args.backlog, args.board)
        with open(args.ziel, "w", encoding="utf-8") as f:
            json.dump(export, f, ensure_ascii=False, indent=1)
        print(f"Export: {len(export['karten'])} Karten, {len(export['stapel'])} Stapel"
              f" (Board {export['boardId']} '{export['titel']}') -> {args.ziel}")
    elif args.befehl == "import":
        with open(args.ziel, encoding="utf-8") as f:
            export = json.load(f)
        sys.exit(0 if app_import(args.app_url.rstrip("/"), args.app_token, export, args.board_titel) else 1)
    elif args.befehl == "diff":
        with open(args.ziel, encoding="utf-8") as f:
            export = json.load(f)
        sys.exit(0 if app_diff(args.app_url.rstrip("/"), args.app_token, export) else 1)


if __name__ == "__main__":
    main()
