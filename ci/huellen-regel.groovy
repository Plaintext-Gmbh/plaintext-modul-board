// Karte 1610: Regel fuer die Abhaengigkeiten eines Moduls, laeuft in validate jedes Moduls (gmavenplus, Wurzel-POM).
//  1. Was der root-BOM verwaltet (dependencyManagement von plaintext-root-parent samt Spring-Boot-BOM), liefert
//     die Huelle: im Modul nur <scope>provided</scope>, auch transitiv (sonst ginge es als Bibliothek mit).
//  2. Eine eigene Bibliothek (compile/runtime) darf keine Klasse enthalten, die schon am provided-Klassenpfad
//     liegt (der Lader bricht beim Start sonst mit "doppelte Klasse" ab, Karte 1514/1520).
// Rot = MojoFailureException mit allen Befunden.
import java.util.zip.ZipFile

if (project.packaging == 'pom') {
    return
}
def key = { a -> a.groupId + ':' + a.artifactId }
def istJar = { a -> a.file != null && a.file.name.endsWith('.jar') }
def klassen = { a ->
    new ZipFile(a.file).withCloseable { z ->
        z.entries().findAll { it.name.endsWith('.class') && !it.name.startsWith('META-INF/') && !it.name.endsWith('module-info.class') }*.name as Set
    }
}
def verwaltet = (project.dependencyManagement?.dependencies ?: []).collect(key) as Set
def mit = project.artifacts.findAll { it.scope in ['compile', 'runtime'] }
// transitiv nur melden, wenn die direkte Abhaengigkeit selbst in Ordnung ist (sonst verschwinden sie mit ihr)
def direktRot = mit.findAll { key(it) in verwaltet && it.dependencyTrail.size() == 2 }.collect { it.dependencyTrail[1] } as Set
def fehler = mit.findAll { key(it) in verwaltet && (it.dependencyTrail.size() == 2 || !(it.dependencyTrail[1] in direktRot)) }.collect {
    "${key(it)}:${it.version} (${it.scope}${it.dependencyTrail.size() > 2 ? ', ueber ' + it.dependencyTrail[1] : ''}): verwaltet der root-BOM, die Huelle liefert es -> <scope>provided</scope>"
}
def huelle = [:]
project.artifacts.findAll { it.scope == 'provided' && istJar(it) }.each { a -> klassen(a).each { huelle[it] = key(a) } }
def eigene = mit.findAll { !(key(it) in verwaltet) && istJar(it) }
eigene.each { a ->
    def doppelt = klassen(a).findAll { huelle.containsKey(it) }.sort()
    if (doppelt) {
        fehler << "${key(a)}:${a.version} doppelt ${doppelt.size()} Klassen der Huelle, z.B. ${doppelt[0]} (aus ${huelle[doppelt[0]]})"
    }
}
if (fehler) {
    throw new org.apache.maven.plugin.MojoFailureException("Huellen-Regel (Karte 1610), ${fehler.size()} Befund(e):\n  " + fehler.join('\n  '))
}
log.info("Huellen-Regel: ${eigene.size()} eigene Bibliothek(en), keine von der Huelle verwaltet oder gedoppelt")
