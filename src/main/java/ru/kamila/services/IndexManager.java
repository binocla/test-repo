package ru.kamila.services;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;

@ApplicationScoped
@Startup
@Slf4j
public class IndexManager {

    @Inject
    Driver driver;

    @PostConstruct
    void configureIndexes() {
        log.info("Checking and creating Neo4j indexes if they don't exist...");
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                            CREATE FULLTEXT INDEX knowledge_search_index IF NOT EXISTS
                            FOR (k:Knowledge)
                            ON EACH [k.title, k.summary, k.authorNames]
                            OPTIONS {
                              indexConfig: {
                                `fulltext.analyzer`: 'standard-folding'
                              }
                            }
                        """);
                return null;
            });
            log.info("Successfully configured Neo4j indexes.");
        }
    }
}
