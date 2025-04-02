package ru.kamila.clients;


import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "https://dspace.kpfu.ru/")
public interface KpfuClient {

    @GET
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    byte[] downloadFile(@Url String url);
}
