#!/usr/bin/env bash
# ci/registry.sh — signiert die eben veroeffentlichten Module und legt sie in die Nextcloud-Registry (Karte 1612).
#
# Laeuft in .woodpecker/release.yml NACH ci/release.sh (gleicher Arbeitsbaum, die Jars liegen in target/).
# Nur beim Ereignis `tag`: die beiden Secrets gibt Woodpecker keinem PR-Lauf.
#   MODUL_SIGNATUR_ED25519    privater Ed25519-Schluessel, PKCS#8 DER Base64 (Vault module.signatur-ed25519)
#   MODUL_REGISTRY_SCHREIBEN  <token>:<passwort> der Schreib-Freigabe (Vault module.registry-schreiben;
#                             lesen/anlegen/aendern, kein Loeschen)
#
# Je Modul: sha256 des Jars = Reposilite, Zeile signieren (Format = ch.plaintext.lader.ModulAuswahl), Jar und
# eigene Bibliotheken hochladen, dann index.txt. Danach alles zuruecklesen und mit dem ECHTEN Lader pruefen
# (ci/IndexPruefen.java): Signatur, sha256, Ort, auch der braucht=-Ziele.
# Karte 1619: Ein Modul, dessen artifactId auf -api endet, steht als Zeile `api` im Index (die Seite bietet es nicht zur
# Wahl an). Haengt ein Modul an einem anderen plaintext-modul-*/plaintext-z-* (auch provided, z.B. fotos -> fotos-api),
# steht es als braucht=<artifactId>:<version> in der signierten Zeile, und der Lader zieht es bei der Auswahl mit.
set -euo pipefail
cd "$(dirname "$0")/.."
DAV="${REGISTRY_DAV:-https://home.plaintext.ch/public.php/webdav}"
BASIS="${REPOSILITE_BASIS:-https://maven.plaintext.ch/releases}"
CENTRAL="https://repo1.maven.org/maven2"
LADER_VERSION="2.812.1"   # erster Lader mit ModulAuswahl + Entwicklungsmodus; nur fuer die Pruefung
sag() { echo "registry: $*"; }
fehler() { echo "registry: FEHLER: $*" >&2; exit 1; }

V="${CI_COMMIT_TAG:-}"
[[ "$V" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fehler "Tag '$V' ist keine Version X.Y.Z"
[ -n "${MODUL_SIGNATUR_ED25519:-}" ] || fehler "MODUL_SIGNATUR_ED25519 fehlt (Woodpecker-Secret modul_signatur_ed25519)"
[[ "${MODUL_REGISTRY_SCHREIBEN:-}" == *:* ]] || fehler "MODUL_REGISTRY_SCHREIBEN fehlt oder ohne ':' (Secret modul_registry_schreiben)"

parent() { awk -v f="$1" '/<parent>/{p=1} p && $0 ~ "<"f">" {gsub(".*<"f">|</"f">.*",""); print; exit}' pom.xml; }
ROOT_MIN="$(parent version)"
[[ "$ROOT_MIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fehler "Parent-Version '$ROOT_MIN' ist keine root-Version"
MODULE="$(sed -n 's#.*<module>\(.*\)</module>.*#\1#p' pom.xml)"   # Verzeichnisse
# Karte 1617: Artefakt = artifactId im Modul-POM (umgezogene Module: Verzeichnis plaintext-z-<x>/, Artefakt plaintext-modul-<x>).
artid() { awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,""); print; exit}' "$1/pom.xml"; }

A=/tmp/ci/registry; rm -rf "$A"; mkdir -p "$A"/neu/jars "$A"/neu/lib "$A"/pruef && chmod 700 /tmp/ci "$A"
trap 'rm -rf "$A"' EXIT
( umask 077
  printf '%s' "$MODUL_SIGNATUR_ED25519" | base64 -d | openssl pkey -inform DER -out "$A/priv.pem"
  printf 'machine %s login %s password %s\n' "$(echo "$DAV" | cut -d/ -f3)" \
      "${MODUL_REGISTRY_SCHREIBEN%%:*}" "${MODUL_REGISTRY_SCHREIBEN#*:}" > "$A/netrc" )
dav() { curl -sS --fail --max-time 300 --netrc-file "$A/netrc" "$@"; }
sha() { sha256sum "$1" | cut -d' ' -f1; }
# signieren <art> <name> <version> <sha256> <braucht> <bibliotheken>: Text = ModulAuswahl.Zeile.nachricht().
# Direkt in die Datei, nicht ueber $(...): das schnitte die abschliessenden Zeilenumbrueche ab.
signieren() { printf 'plaintext-modul-signatur-1\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n' "$1" "$2" "$3" "$ROOT_MIN" "$4" "$5" "$6" > "$A/nachricht"
    openssl pkeyutl -sign -rawin -inkey "$A/priv.pem" -in "$A/nachricht" | base64 -w0; }

dav -o "$A/alt.txt" "$DAV/index.txt" || fehler "index.txt der Registry nicht lesbar"
[ "$(head -1 "$A/alt.txt")" = "plaintext-modul-index 1" ] || fehler "index.txt der Registry hat keinen gueltigen Kopf"

ARTIDS=" $(for m in $MODULE; do artid "$m"; done | tr '\n' ' ')"
ZEILEN=(); BIBS=(); PRUEFEN=()
for m in $MODULE; do
    a="$(artid "$m")"
    jar="$m/target/$a-$V.jar"
    [ -f "$jar" ] || fehler "$jar fehlt (lief ci/release.sh vorher?)"
    s="$(sha "$jar")"
    curl -sS --fail --max-time 120 -o "$A/reposilite.jar" "$BASIS/ch/plaintext/$a/$V/$a-$V.jar" \
        || fehler "$a-$V.jar nicht in Reposilite"
    [ "$(sha "$A/reposilite.jar")" = "$s" ] || fehler "$a-$V.jar: sha256 lokal != Reposilite"
    cp "$jar" "$A/neu/jars/"
    # Abhaengigkeiten ohne test: compile/runtime = was das Modul selbst mitbringen muss; provided liefert die Huelle,
    # ausser ein anderes Modul (braucht=). -am compile (Karte 1601): ein Schwestermodul (fotos-api, provided) wird im
    # Reaktor aufgeloest, sonst sucht Maven es in Reposilite. Die Ausgabedatei schreibt das zuletzt gebaute Modul, also
    # "$m" selbst; das signierte Jar aus release.sh bleibt unberuehrt (kein clean).
    mvn -B -q -pl "$m" -am compile dependency:list -DoutputAbsoluteArtifactFilename=true \
        -DoutputFile="$A/deps.txt" -Dmaven.build.cache.enabled=false
    bib=(); braucht=()
    grep -E ':(compile|runtime|provided):' "$A/deps.txt" | sed 's/^ *//; s/ .*//' > "$A/deps-roh.txt" || true
    while IFS=: read -r g art typ ver scope pfad rest; do
        [ -z "$rest" ] && [ -n "$pfad" ] || fehler "$a: Abhaengigkeit '$g:$art:$typ:$ver:$scope…' nicht lesbar (Classifier?)"
        case "$g:$art" in ch.plaintext:plaintext-modul-*|ch.plaintext:plaintext-z-*)
            # Schwestermodul: die POM-Version ist hier wieder -SNAPSHOT (release.sh setzt sie nur fuer seinen Lauf)
            case "$ARTIDS" in *" $art "*) ver="$V" ;; esac
            [[ "$ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fehler "$a braucht $art in Version '$ver' (keine Release-Version)"
            braucht+=("$art:$ver"); continue ;;
        esac
        [ "$scope" = provided ] && continue
        case "$g" in ch.plaintext*) fehler "$a: $g:$art mit Scope $scope - liefert die Huelle, gehoert provided" ;; esac
        [ "$typ" = jar ] || fehler "$a: Abhaengigkeit $g:$art ist vom Typ $typ, kein Jar"
        curl -sS --fail --max-time 120 -o "$A/central.jar" "$CENTRAL/${g//.//}/$art/$ver/$art-$ver.jar" \
            || fehler "Bibliothek $g:$art:$ver nicht auf Maven Central"
        bs="$(sha "$A/central.jar")"
        [ "$bs" = "$(sha "$pfad")" ] || fehler "Bibliothek $g:$art:$ver: sha256 lokal != Central"
        cp "$A/central.jar" "$A/neu/lib/$art-$ver.jar"
        bib+=("$g:$art:$ver@$bs"); BIBS+=("bib $g:$art $ver datei=lib/$art-$ver.jar")
    done < "$A/deps-roh.txt"
    art=modul; [[ "$a" == *-api ]] && art=api
    bibl="$(IFS=,; echo "${bib[*]}")"; br="$(IFS=,; echo "${braucht[*]}")"
    sig="$(signieren "$art" "$a" "$V" "$s" "$br" "$bibl")"
    z="$art $a $V root-min=$ROOT_MIN sha256=$s"; [ -z "$br" ] || z="$z braucht=$br"; [ -z "$bibl" ] || z="$z bibliotheken=$bibl"
    ZEILEN+=("$z datei=jars/$a-$V.jar signatur=$sig"); PRUEFEN+=("$a:$V")
    sag "$art $a $V signiert (sha256 ${s:0:12}…, braucht '${br}', ${#bib[@]} Bibliothek(en), root-min $ROOT_MIN)"
done

# Index: Zeilen derselben (art, name, version) ersetzen, Rest behalten (gleich modul-index.py).
{ echo "plaintext-modul-index 1"; echo "# erzeugt von plaintext-modules/scripts/modul-index.py (Karte 1608), ergaenzt von ci/registry.sh (Karte 1612)"
  { tail -n +2 "$A/alt.txt" | grep -v '^#' | grep -v '^[[:space:]]*$' | while read -r art n ver rest; do
        for p in "${PRUEFEN[@]}"; do [ "$art" != bib ] && [ "$n:$ver" = "$p" ] && continue 2; done
        echo "$art $n $ver $rest"; done
    printf '%s\n' "${ZEILEN[@]}" "${BIBS[@]}"; } | sort -u; } > "$A/neu/index.txt"

for f in "$A"/neu/jars/* "$A"/neu/lib/*; do
    [ -f "$f" ] || continue
    d="$(basename "$(dirname "$f")")"
    dav -X MKCOL -o /dev/null "$DAV/$d/" 2>/dev/null || true   # gibt es den Ordner schon: 405
    dav -T "$f" -o /dev/null "$DAV/$d/$(basename "$f")"; sag "hochgeladen: $d/$(basename "$f")"
done
dav -T "$A/neu/index.txt" -o /dev/null "$DAV/index.txt"; sag "hochgeladen: index.txt ($(($(wc -l < "$A/neu/index.txt") - 2)) Zeilen)"

# Zuruecklesen (auch gegen einen gleichzeitigen Lauf, der index.txt ueberschrieben haette) und mit dem Lader pruefen.
dav -o "$A/pruef/index.txt" "$DAV/index.txt"
for z in "${ZEILEN[@]}"; do grep -qxF "$z" "$A/pruef/index.txt" || fehler "Zeile fehlt nach dem Hochladen (paralleler Lauf?): ${z%% sha256=*}"; done
mkdir -p "$A/pruef/jars" "$A/pruef/lib"
for p in "${PRUEFEN[@]}"; do f="jars/${p%%:*}-${p#*:}.jar"; dav -o "$A/pruef/$f" "$DAV/$f"; done
for b in "${BIBS[@]}"; do f="${b##*datei=}"; dav -o "$A/pruef/$f" "$DAV/$f"; done
# Karte 1624: braucht-Ziel aus einem ANDEREN Repo (verrechnung -> messenger-api) liegt schon in der Registry; der Lader
# prueft es mit, also mitlesen. ponytail: Bibliotheken des Ziels werden nicht mitgelesen (messenger-api hat keine).
for z in "${ZEILEN[@]}"; do
    for b in $(sed -n 's/.* braucht=\([^ ]*\).*/\1/p' <<<"$z" | tr ',' ' '); do
        f="jars/${b%%:*}-${b#*:}.jar"; [ -f "$A/pruef/$f" ] || dav -o "$A/pruef/$f" "$DAV/$f"
    done
done
curl -sS --fail --max-time 120 -o "$A/lader.jar" "$BASIS/ch/plaintext/v2/plaintext-root-lader/$LADER_VERSION/plaintext-root-lader-$LADER_VERSION.jar"
[ "$(sha1sum "$A/lader.jar" | cut -d' ' -f1)" = "$(curl -sS --fail "$BASIS/ch/plaintext/v2/plaintext-root-lader/$LADER_VERSION/plaintext-root-lader-$LADER_VERSION.jar.sha1" | cut -c1-40)" ] \
    || fehler "lader.jar: sha1 passt nicht"
{ grep '^schluessel ' ci/modul-schluessel.txt; echo "registry ci file://$A/pruef/index.txt"; } > "$A/quellen.txt"
javac -cp "$A/lader.jar" -d "$A/klassen" ci/IndexPruefen.java
java -cp "$A/lader.jar:$A/klassen" ch.plaintext.lader.IndexPruefen "$A/quellen.txt" "$A/cache" "${PRUEFEN[@]}"
sag "OK: ${PRUEFEN[*]} signiert in der Registry, vom Lader geprueft"
