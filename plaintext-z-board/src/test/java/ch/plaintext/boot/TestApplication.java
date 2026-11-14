package ch.plaintext.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test application for the board module: scans ch.plaintext, JPA repositories and entities.
 */
@SpringBootApplication(scanBasePackages = {"ch.plaintext"})
@EnableJpaRepositories(basePackages = {"ch.plaintext"})
@EntityScan(basePackages = {"ch.plaintext"})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
