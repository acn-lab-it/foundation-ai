package com.accenture.claims.ai.adapter;

import com.accenture.claims.ai.adapter.inbound.rest.dto.email.DownloadedAttachment;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.EmailDto;
import com.accenture.claims.ai.port.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@ApplicationScoped
public class EmailServiceAdapter implements EmailService {

    ObjectMapper mapper;

    @ConfigProperty(name = "email.service.base-url")
    String baseUrl;

    @PostConstruct
    public void init() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    public EmailDto findOne(String id) throws Exception {
        try (
                HttpClient client = HttpClient.newHttpClient()) {

            URI uri = new URIBuilder(baseUrl + "/api/emails/" + id).build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String json = response.body();

            return mapper.readValue(json, EmailDto.class);

        } catch (Exception e) {
            //TODO: gestione degli errori
            System.err.println("Si è verificato un errore");
            e.printStackTrace();
            throw e;
        }
    }

    public Map<String, Object> findAll(int page, int size) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {

            URI uri = new URIBuilder(baseUrl + "/api/emails")
                    .addParameter("page", String.valueOf(page))
                    .addParameter("size", String.valueOf(size))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String json = response.body();

            return mapper.readValue(json, Map.class);

        } catch (Exception e) {
            //TODO: gestione degli errori
            System.err.println("Si è verificato un errore");
            e.printStackTrace();
            throw e;
        }
    }

    public DownloadedAttachment downloadAttachment(String emailId, String filename) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {

            URI uri = new URIBuilder(baseUrl + "/api/emails/" + emailId + "/attachments/" + filename).build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download file: status = " + response.statusCode());
            }

            byte[] content = response.body();
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");

            String contentDisposition = response.headers()
                    .firstValue("Content-Disposition")
                    .orElse("attachment; filename=\"" + filename + "\"");

            return new DownloadedAttachment(content, contentType, contentDisposition);

        } catch (Exception e) {
            //TODO: gestione degli errori
            System.err.println("Si è verificato un errore");
            e.printStackTrace();
            throw e;
        }
    }
}
