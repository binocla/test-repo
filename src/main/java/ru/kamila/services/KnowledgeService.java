package ru.kamila.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jsoup.Jsoup;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import ru.kamila.clients.KpfuClient;
import ru.kamila.entities.KnowledgeEntity;
import ru.kamila.models.KnowledgeRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class KnowledgeService {
    private static final String FULL_QUERY = "?show=full";

    @RestClient
    KpfuClient kpfuClient;

    @Inject
    Driver driver;

    public KnowledgeEntity createKnowledge(KnowledgeRequest knowledgeRequest) throws IOException {
        var idFromUrl = StringUtils.substringAfterLast(knowledgeRequest.url(), "/");
        log.info("Creating knowledge for URL id: {}", idFromUrl);

        var doc = Jsoup.connect(knowledgeRequest.url() + FULL_QUERY).get();

        var authors = new ArrayList<String>();
        int creationDate = 0;
        var issuerId = "";
        var summary = "";
        var title = "";
        var type = "";

        for (var meta : doc.select("meta")) {
            var name = meta.attr("name");
            var content = meta.attr("content");
            switch (name) {
                case "DC.creator" -> authors.add(content);
                case "citation_date" -> creationDate = Integer.parseInt(content);
                case "citation_issn" -> issuerId = content;
                case "DCTERMS.abstract" -> summary = content;
                case "DC.title" -> title = content;
                case "DC.type" -> type = content;
            }
        }

        String fileDownloadUrl = null;
        for (var a : doc.select("a[href]")) {
            if (a.attr("href").contains("file")) {
                fileDownloadUrl = "https://dspace.kpfu.ru" + a.attr("href")
                        .replace("viewer?file=27232;", "bitstream/handle/net/" + idFromUrl + "/")
                        .replace("&", "?");
                break;
            }
        }
        var fileBytes = kpfuClient.downloadFile(fileDownloadUrl);

        var knowledgeId = UUID.randomUUID();
        var knowledge = new KnowledgeEntity(knowledgeId, authors, creationDate, issuerId, summary, title, type, fileBytes);

        try (var session = driver.session()) {
            var cypher = """
                    MERGE (k:Knowledge {id: $id})
                    SET k.creationDate = $creationDate, 
                        k.issuerId = $issuerId, 
                        k.summary = $summary,
                        k.title = $title, 
                        k.type = $type, 
                        k.file = $file
                    WITH k
                    UNWIND $authors as authorName
                    MERGE (a:Author {name: authorName})
                    ON CREATE SET a.id = randomUUID()
                    MERGE (k)-[:WRITTEN_BY]->(a)
                    RETURN k
                    """;
            session.executeWrite(tx -> {
                tx.run(cypher, Values.parameters(
                        "id", knowledge.getId().toString(),
                        "creationDate", knowledge.getCreationDate(),
                        "issuerId", knowledge.getIssuerId(),
                        "summary", knowledge.getSummary(),
                        "title", knowledge.getTitle(),
                        "type", knowledge.getType(),
                        "file", knowledge.getFile(),
                        "authors", knowledge.getAuthors()
                ));
                return null;
            });
        }

        return knowledge;
    }

    public KnowledgeEntity getKnowledge(String id) {
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (k:Knowledge {id: $id})
                    OPTIONAL MATCH (k)-[:WRITTEN_BY]->(a:Author)
                    RETURN k, collect(a.name) as authors
                    """;
            var record = session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("id", id));
                return result.single();
            });
            var kNode = record.get("k").asNode();
            var authors = record.get("authors").asList(Value::asString);
            return KnowledgeEntity.from(kNode, authors);
        }
    }

    public List<KnowledgeEntity> searchKnowledges(String searchText, int page, int size) {
        int skip = page * size;
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (k:Knowledge)
                    OPTIONAL MATCH (k)-[:WRITTEN_BY]->(a:Author)
                    WHERE toLower(k.summary) CONTAINS toLower($searchText)
                       OR toLower(k.title) CONTAINS toLower($searchText)
                       OR toLower(a.name) CONTAINS toLower($searchText)
                    WITH k, collect(a.name) as authors
                    RETURN k, authors SKIP $skip LIMIT $limit
                    """;
            var result = session.executeRead(tx ->
                    tx.run(cypher, Values.parameters(
                            "searchText", searchText,
                            "skip", skip,
                            "limit", size
                    )).list());
            var knowledges = new ArrayList<KnowledgeEntity>();
            for (var record : result) {
                var kNode = record.get("k").asNode();
                var authorsList = record.get("authors").asList(Value::asString);
                knowledges.add(KnowledgeEntity.from(kNode, authorsList));
            }
            return knowledges;
        }
    }

    public List<KnowledgeEntity> getAllKnowledges(int page, int size) {
        var skip = page * size;
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (k:Knowledge)
                    OPTIONAL MATCH (k)-[:WRITTEN_BY]->(a:Author)
                    WITH k, collect(a.name) as authors
                    RETURN k, authors SKIP $skip LIMIT $limit
                    """;
            var result = session.executeRead(tx ->
                    tx.run(cypher, Values.parameters(
                            "skip", skip,
                            "limit", size
                    )).list());
            var knowledges = new ArrayList<KnowledgeEntity>();
            for (var record : result) {
                var kNode = record.get("k").asNode();
                var authorsList = record.get("authors").asList(Value::asString);
                knowledges.add(KnowledgeEntity.from(kNode, authorsList));
            }
            return knowledges;
        }
    }

    public List<KnowledgeEntity> getKnowledgesByAuthor(String authorId, int page, int size) {
        var skip = page * size;
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (a:Author {id: $authorId})<-[:WRITTEN_BY]-(k:Knowledge)
                    OPTIONAL MATCH (k)-[:WRITTEN_BY]->(other:Author)
                    WITH k, collect(other.name) as authors
                    RETURN k, authors SKIP $skip LIMIT $limit
                    """;
            var result = session.executeRead(tx ->
                    tx.run(cypher, Values.parameters(
                            "authorId", authorId,
                            "skip", skip,
                            "limit", size
                    )).list());
            var knowledges = new ArrayList<KnowledgeEntity>();
            for (var record : result) {
                var kNode = record.get("k").asNode();
                var authorsList = record.get("authors").asList(Value::asString);
                knowledges.add(KnowledgeEntity.from(kNode, authorsList));
            }
            return knowledges;
        }
    }
}
