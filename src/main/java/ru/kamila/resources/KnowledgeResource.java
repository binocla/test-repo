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
import ru.kamila.entities.KnowledgeEntity;
import ru.kamila.models.KnowledgeRequest;
import ru.kamila.services.KnowledgeService;

import java.util.List;

@Path("/api/v1/knowledge")
public class KnowledgeResource {

    @Inject
    KnowledgeService knowledgeService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createKnowledge(@Valid KnowledgeRequest knowledgeRequest) {
        try {
            KnowledgeEntity created = knowledgeService.createKnowledge(knowledgeRequest);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKnowledge(@PathParam("id") String id) {
        try {
            KnowledgeEntity knowledge = knowledgeService.getKnowledge(id);
            if (knowledge == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(knowledge).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
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
                results = knowledgeService.searchKnowledges(search, page, size);
            } else {
                results = knowledgeService.getAllKnowledges(page, size);
            }
            return Response.ok(results).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
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
            List<KnowledgeEntity> results = knowledgeService.getKnowledgesByAuthor(authorId, page, size);
            return Response.ok(results).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
