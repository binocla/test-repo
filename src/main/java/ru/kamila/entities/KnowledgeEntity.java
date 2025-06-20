package ru.kamila.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeEntity {
    private UUID id;
    private List<String> authors = new ArrayList<>();
    private Integer creationDate;
    private String issuerId;
    private String summary;
    private String title;
    private String type;
    @ToString.Exclude
    @JsonIgnore
    private byte[] file;

    public KnowledgeEntity(UUID id, List<String> authors, Integer creationDate, String issuerId, String summary, String title, String type) {
        this.id = id;
        this.authors = authors;
        this.creationDate = creationDate;
        this.issuerId = issuerId;
        this.summary = summary;
        this.title = title;
        this.type = type;
    }

    public static KnowledgeEntity from(Node node, List<String> authors) {
        return new KnowledgeEntity(
                UUID.fromString(node.get("id").asString()),
                authors,
                node.get("creationDate").asInt(),
                node.get("issuerId").asString(),
                node.get("summary").asString(),
                node.get("title").asString(),
                node.get("type").asString()
        );
    }
}
