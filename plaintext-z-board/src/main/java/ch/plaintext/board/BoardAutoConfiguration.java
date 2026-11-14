/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.board;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Einbau ohne eigenen Code (Karte 2193): das Jar auf den Klassenpfad, fertig. Spring Boot lädt diese
 * Klasse über {@code META-INF/spring/…AutoConfiguration.imports}; sie meldet REST-API, Service,
 * MCP-Werkzeuge und (spaeter) die Kanban-Seite an. Entitaeten und Repositories findet die App ueber
 * ihren eigenen EntityScan/EnableJpaRepositories (ch.plaintext).
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = BoardAutoConfiguration.class)
public class BoardAutoConfiguration {
}
