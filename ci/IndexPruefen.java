package ch.plaintext.lader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Karte 1612: prueft eine Registry mit dem ECHTEN Lader (plaintext-root-lader auf dem Klassenpfad, gleiches Paket,
 * darum die paketsichtbaren Methoden): Index lesbar, je genanntem Modul Signatur gueltig, Datei am Ursprung,
 * sha256 der geladenen Datei = signierte Zeile, ebenso fuer seine Bibliotheken und (Karte 1619) die Module aus
 * {@code braucht=}, die der Lader bei der Auswahl mitzieht.
 *
 *   javac -cp lader.jar -d out ci/IndexPruefen.java
 *   java -cp lader.jar:out ch.plaintext.lader.IndexPruefen <module-quellen.txt> <cache-verzeichnis> <name:version> ...
 * (nicht als Quelldatei starten: deren eigener Klassenlader waere ein anderes Laufzeit-Paket.)
 */
public class IndexPruefen {
    public static void main(String[] args) throws Exception {
        ModulAuswahl.Quellen q = ModulAuswahl.quellenLesen(Path.of(args[0]));
        ModulAuswahl.Angebot angebot = ModulAuswahl.angebot(q, new ModulQuelle());
        if (!angebot.fehler().isEmpty()) {
            throw new IllegalStateException("Registry nicht lesbar: " + angebot.fehler());
        }
        Path cache = Files.createDirectories(Path.of(args[1]));
        for (int i = 2; i < args.length; i++) {
            String wahl = args[i];
            String[] nv = wahl.split(":");
            ModulAuswahl.Zeile z = angebot.zeilen().stream()
                    .filter(x -> !x.istBibliothek() && x.name().equals(nv[0]) && x.version().equals(nv[1])).findFirst()
                    .orElseThrow(() -> new IllegalStateException(wahl + " steht nicht im Index"));
            ModulAuswahl.signaturPruefen(z, q.schluessel());
            ModulAuswahl.bereitstellen(z, cache, new ModulQuelle(), true);
            for (ModulAuswahl.Zeile b : ModulAuswahl.bibliotheken(java.util.List.of(z), angebot.zeilen())) {
                ModulAuswahl.bereitstellen(b, cache, new ModulQuelle(), true);
            }
            for (String b : z.braucht()) {
                String[] bv = b.split(":");
                ModulAuswahl.Zeile d = angebot.zeilen().stream()
                        .filter(x -> !x.istBibliothek() && x.name().equals(bv[0]) && x.version().equals(bv[1])).findFirst()
                        .orElseThrow(() -> new IllegalStateException(wahl + " braucht " + b + ", das steht nicht im Index"));
                ModulAuswahl.signaturPruefen(d, q.schluessel());
                ModulAuswahl.bereitstellen(d, cache, new ModulQuelle(), true);
            }
            System.out.println("[index-pruefen] " + z.art() + " " + wahl + ": Signatur gueltig, sha256 = signierte Zeile, "
                    + z.bibliotheken().size() + " Bibliothek(en), braucht " + z.braucht());
        }
    }
}
