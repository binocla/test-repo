package ru.kamila.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.driver.types.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeEntity {
    private UUID id;
    private List<String> authors = new ArrayList<>();
    private Integer creationDate;
    private String issuerId;
    private String summary;
    private String title;
    private String type;
    @ToString.Exclude
    private byte[] file;

    public static KnowledgeEntity from(Node node, List<String> authors) {
        return new KnowledgeEntity(
                UUID.fromString(node.get("id").asString()),
                authors,
                node.get("creationDate").asInt(),
                node.get("issuerId").asString(),
                node.get("summary").asString(),
                node.get("title").asString(),
                node.get("type").asString(),
                node.get("file").asByteArray()
        );
    }
}
