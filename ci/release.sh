#!/usr/bin/env bash
# ci/release.sh — Release eines Modul-Repos nach Reposilite (Karte 1599, Muster v2/release.sh).
#
# Ausloeser ist der Git-Tag (Woodpecker-Ereignis `tag`, .woodpecker/release.yml). Der Tag IST die
# Version (X.Y.Z, ohne Praefix). Darum hier kein Tag-Push und kein Push-Token: wer taggt, hat ihn schon
# gepusht. Die POM-Version wird nur im Arbeitsbaum gesetzt, nie committet (kein "Release version"-Commit,
# master bleibt auf X.Y.Z-SNAPSHOT).
#
#   ci/release.sh             Release (braucht CI_COMMIT_TAG und MAVEN_NAS_TOKEN)
#   ci/release.sh --trocken   nur pruefen und anzeigen, was veroeffentlicht wuerde
#
# Umgebung: CI_COMMIT_TAG (Woodpecker), MAVEN_NAS_TOKEN (Secret maven_nas_token, Server-Id plaintext-nas),
#           REPOSILITE_BASIS (Standard https://maven.plaintext.ch/releases)
set -euo pipefail

TROCKEN=0; [ "${1:-}" = "--trocken" ] && TROCKEN=1
cd "$(dirname "$0")/.."
BASIS="${REPOSILITE_BASIS:-https://maven.plaintext.ch/releases}"
sag() { echo "release: $*"; }
fehler() { echo "release: FEHLER: $*" >&2; exit 1; }

V="${CI_COMMIT_TAG:-}"
[[ "$V" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fehler "Tag '$V' ist keine Version X.Y.Z"

# Koordinaten aus der Wurzel-POM: groupId/artifactId nach dem <parent>-Block, Module aus <modules>.
feld() { awk -v f="$1" '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && $0 ~ "<"f">" {gsub(".*<"f">|</"f">.*",""); print; exit}' pom.xml; }
GRUPPE="$(feld groupId)"; WURZEL="$(feld artifactId)"
[ "$GRUPPE" = "ch.plaintext" ] || fehler "groupId '$GRUPPE' — Module liegen unter ch.plaintext (Vorgabe des Laders)"
# Karte 1617: Artefakt = artifactId im Modul-POM, nicht der Verzeichnisname (umgezogene Module behalten
# ihr Verzeichnis plaintext-z-<x>/, heissen aber plaintext-modul-<x>).
artid() { awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,""); print; exit}' "$1/pom.xml"; }
MODULE="$(for m in $(sed -n 's#.*<module>\(.*\)</module>.*#\1#p' pom.xml); do artid "$m"; done)"
[ -n "$MODULE" ] || fehler "keine <module> in pom.xml"
GPFAD="$(echo "$GRUPPE" | tr . /)"

code() { curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$1" || true; }
for a in $WURZEL $MODULE; do
    c="$(code "$BASIS/$GPFAD/$a/$V/$a-$V.pom")"
    case "$c" in
        200) fehler "$GRUPPE:$a:$V liegt schon in Reposilite — Versionen sind unveraenderlich, neuen Tag nehmen" ;;
        404) sag "$a:$V ist frei" ;;
        *)   fehler "Reposilite antwortet $c fuer $a — ohne Kollisionspruefung kein Release" ;;
    esac
done

if [ "$TROCKEN" = 1 ]; then
    sag "TROCKEN: wuerde $GRUPPE {$WURZEL $MODULE}:$V bauen und nach $BASIS deployen."
    exit 0
fi
[ -n "${MAVEN_NAS_TOKEN:-}" ] || fehler "MAVEN_NAS_TOKEN fehlt (Woodpecker-Secret maven_nas_token)"

# Zugangsdaten nur in /tmp/ci, nie im geteilten /root/.m2.
mkdir -p /tmp/ci && chmod 700 /tmp/ci
( umask 077; printf '%s\n' '<settings><servers><server><id>plaintext-nas</id><username>ci</username>' \
    "<password>$MAVEN_NAS_TOKEN</password></server></servers></settings>" > /tmp/ci/settings.xml )
trap 'rm -f /tmp/ci/settings.xml; git checkout -q -- pom.xml "*/pom.xml" 2>/dev/null || true' EXIT

mvn -B -q versions:set -DnewVersion="$V" -DgenerateBackupPoms=false -DprocessAllModules=true
mvn -B -s /tmp/ci/settings.xml clean deploy -Dmaven.build.cache.enabled=false

# Gegenprobe: jedes Artefakt mit .pom, .jar, -sources.jar (ausser der Wurzel) und .sha1 abrufbar.
for a in $WURZEL $MODULE; do
    endungen=".pom .pom.sha1"; [ "$a" = "$WURZEL" ] || endungen="$endungen .jar .jar.sha1 -sources.jar"
    for e in $endungen; do
        c="$(code "$BASIS/$GPFAD/$a/$V/$a-$V$e")"
        [ "$c" = 200 ] || fehler "$a-$V$e: HTTP $c nach dem Deploy"
    done
    sag "$GRUPPE:$a:$V in Reposilite ($endungen: 200)"
done
