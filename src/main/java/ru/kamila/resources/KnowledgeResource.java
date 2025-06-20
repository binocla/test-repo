package ru.kamila.resources;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import ru.kamila.entities.KnowledgeEntity;
import ru.kamila.models.KnowledgeRequest;
import ru.kamila.services.KnowledgeService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/knowledge")
@Slf4j
public class KnowledgeResource {

    @Inject
    KnowledgeService knowledgeService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createKnowledge(@Valid KnowledgeRequest knowledgeRequest) {
        log.info("Received request to create knowledge for URL: {}", knowledgeRequest.url());
        try {
            var created = knowledgeService.createKnowledge(knowledgeRequest);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (Exception e) {
            log.error("Error creating knowledge", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/{id}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadKnowledgeFile(@PathParam("id") String id) {
        try {
            var file = knowledgeService.getKnowledgeFile(id);
            if (file == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            var randomUuid = UUID.randomUUID().toString();
            return Response.ok(file)
                    .header("Content-Disposition", "attachment; filename=\"" + randomUuid + ".pdf\"")
                    .build();
        } catch (Exception e) {
            log.error("Error downloading file for knowledge id {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKnowledge(@PathParam("id") String id) {
        try {
            var knowledge = knowledgeService.getKnowledge(id);
            if (knowledge == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(knowledge).build();
        } catch (Exception e) {
            log.error("Error getting knowledge with id {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listKnowledges(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        try {
            List<KnowledgeEntity> results;
            if (search != null && !search.isBlank()) {
                log.info("Searching for knowledges with query: {}", search);
                results = knowledgeService.searchKnowledges(search, page, size);
            } else {
                log.info("Fetching all knowledges");
                results = knowledgeService.getAllKnowledges(page, size);
            }
            return Response.ok(results).build();
        } catch (Exception e) {
            log.error("Error listing knowledges", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/author/{authorId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKnowledgesByAuthor(
            @PathParam("authorId") String authorId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        try {
            var results = knowledgeService.getKnowledgesByAuthor(authorId, page, size);
            return Response.ok(results).build();
        } catch (Exception e) {
            log.error("Error getting knowledges for author {}", authorId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/{id}/recommendations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRecommendations(
            @PathParam("id") String knowledgeId,
            @QueryParam("limit") @DefaultValue("5") int limit
    ) {
        try {
            var recommendations = knowledgeService.getAuthorBasedRecommendations(knowledgeId, limit);
            return Response.ok(recommendations).build();
        } catch (Exception e) {
            log.error("Error getting recommendations for knowledge {}", knowledgeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}
