package ru.kamila.models;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record KnowledgeRequest(
        @NotBlank(message = "URL must not be blank")
        @URL(message = "String must be URL")
        String url) {
}
