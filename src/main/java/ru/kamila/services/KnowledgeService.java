package ru.kamila.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jsoup.Jsoup;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import ru.kamila.clients.KpfuClient;
import ru.kamila.entities.KnowledgeEntity;
import ru.kamila.models.KnowledgeRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class KnowledgeService {
    private static final String FULL_QUERY = "?show=full";

    @RestClient
    KpfuClient kpfuClient;

    @Inject
    Driver driver;

    public KnowledgeEntity createKnowledge(KnowledgeRequest knowledgeRequest) throws IOException {
        log.info("Starting to create knowledge from URL: {}", knowledgeRequest.url());
        var idFromUrl = StringUtils.substringAfterLast(knowledgeRequest.url(), "/");

        var doc = Jsoup.connect(knowledgeRequest.url() + FULL_QUERY).get();

        var authors = new ArrayList<String>();
        int creationDate = 0;
        var issuerId = "";
        var summary = "";
        var title = "";
        var type = "";
        String fileDownloadUrl = null;

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

        for (var a : doc.select("a[href]")) {
            if (a.attr("href").contains("file")) {
                fileDownloadUrl = "https://dspace.kpfu.ru" + a.attr("href")
                        .replace("viewer?file=27232;", "bitstream/handle/net/" + idFromUrl + "/")
                        .replace("&", "?");
                break;
            }
        }

        byte[] fileBytes = null;
        if (fileDownloadUrl != null) {
            log.info("Downloading file from: {}", fileDownloadUrl);
            fileBytes = kpfuClient.downloadFile(fileDownloadUrl);
        } else {
            log.warn("Could not find a downloadable file link on page: {}", knowledgeRequest.url());
        }

        var knowledgeId = UUID.randomUUID();
        var knowledge = new KnowledgeEntity(knowledgeId, authors, creationDate, issuerId, summary, title, type, fileBytes);

        var authorNamesString = String.join(" ", authors);

        try (var session = driver.session()) {
            var cypher = """
                    MERGE (k:Knowledge {id: $id})
                    SET k.creationDate = $creationDate,
                        k.issuerId = $issuerId,
                        k.summary = $summary,
                        k.title = $title,
                        k.type = $type,
                        k.file = $file,
                        k.authorNames = $authorNames
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
                        "authorNames", authorNamesString,
                        "authors", knowledge.getAuthors()
                ));
                return null;
            });
        }
        log.info("Successfully created knowledge node with ID: {}", knowledge.getId());
        knowledge.setFile(null);
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
                return result.hasNext() ? result.single() : null;
            });
            return record != null ? recordToKnowledgeEntity(record) : null;
        }
    }

    public byte[] getKnowledgeFile(String id) {
        try (var session = driver.session()) {
            var cypher = "MATCH (k:Knowledge {id: $id}) RETURN k.file as file";
            return session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("id", id));
                if (result.hasNext()) {
                    Value fileValue = result.single().get("file");
                    return fileValue.isNull() ? null : fileValue.asByteArray();
                }
                return null;
            });
        }
    }

    public List<KnowledgeEntity> searchKnowledges(String searchText, int page, int size) {
        var skip = page * size;

        var enhancedQuery = Arrays.stream(searchText.trim().split("\\s+"))
                .filter(term -> !term.isEmpty())
                .map(term -> "(" + term + "* OR " + term + "~1)")
                .collect(Collectors.joining(" AND "));

        log.info("Executing enhanced search with query: {}", enhancedQuery);

        try (var session = driver.session()) {
            var cypher = """
                    CALL db.index.fulltext.queryNodes("knowledge_search_index", $searchText) YIELD node, score
                    MATCH (node)-[:WRITTEN_BY]->(a:Author)
                    WITH node AS k, score, collect(a.name) as authors
                    RETURN k, authors, score
                    ORDER BY score DESC
                    SKIP $skip LIMIT $limit
                    """;
            var result = session.executeRead(tx ->
                    tx.run(cypher, Values.parameters("searchText", enhancedQuery, "skip", skip, "limit", size)).list());
            return recordsToKnowledgeEntityList(result);
        }
    }

    public List<KnowledgeEntity> getAllKnowledges(int page, int size) {
        var skip = page * size;
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (k:Knowledge)
                    OPTIONAL MATCH (k)-[:WRITTEN_BY]->(a:Author)
                    WITH k, collect(a.name) as authors
                    RETURN k, authors
                    ORDER BY k.creationDate DESC
                    SKIP $skip LIMIT $limit
                    """;
            var result = session.executeRead(tx ->
                    tx.run(cypher, Values.parameters("skip", skip, "limit", size)).list());
            return recordsToKnowledgeEntityList(result);
        }
    }

    public List<KnowledgeEntity> getKnowledgesByAuthor(String authorId, int page, int size) {
        var skip = page * size;
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (a:Author {id: $authorId})<-[:WRITTEN_BY]-(k:Knowledge)
                    OPTIONAL MATCH (k)-[:WRITTEN_BY]->(other:Author)
                    WITH k, collect(other.name) as authors
                    RETURN k, authors
                    ORDER BY k.creationDate DESC
                    SKIP $skip LIMIT $limit
                    """;
            var result = session.executeRead(tx ->
                    tx.run(cypher, Values.parameters("authorId", authorId, "skip", skip, "limit", size)).list());
            return recordsToKnowledgeEntityList(result);
        }
    }

    public List<KnowledgeEntity> getAuthorBasedRecommendations(String knowledgeId, int limit) {
        try (var session = driver.session()) {
            var cypher = """
                    MATCH (source:Knowledge {id: $knowledgeId})-[:WRITTEN_BY]->(a:Author)
                    MATCH (rec:Knowledge)-[:WRITTEN_BY]->(a)
                    WHERE source <> rec
                    WITH rec, count(a) AS sharedAuthors
                    OPTIONAL MATCH (rec)-[:WRITTEN_BY]->(allAuthors:Author)
                    WITH rec, sharedAuthors, collect(allAuthors.name) AS authors
                    RETURN rec AS k, authors, sharedAuthors
                    ORDER BY sharedAuthors DESC
                    LIMIT $limit
                    """;
            var result = session.executeRead(tx -> tx.run(cypher, Values.parameters("knowledgeId", knowledgeId, "limit", limit)).list());
            return recordsToKnowledgeEntityList(result);
        }
    }

    private List<KnowledgeEntity> recordsToKnowledgeEntityList(List<Record> records) {
        return records.stream()
                .map(this::recordToKnowledgeEntity)
                .collect(Collectors.toList());
    }

    private KnowledgeEntity recordToKnowledgeEntity(Record record) {
        var kNode = record.get("k").asNode();
        var authors = record.get("authors").asList(Value::asString);
        return KnowledgeEntity.from(kNode, authors);
    }
}
