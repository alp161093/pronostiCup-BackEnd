package com.pronosticup.backend.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final RestClient.Builder restClientBuilder;

    @Value("${brevo.api.url}")
    private String brevoApiUrl;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${app.mail.from}")
    private String from;

    /**
     * Email con adjuntos
     */
    public void sendEmailWithAttachments(
            String to,
            String subject,
            String body,
            List<EmailAttachment> attachments
    ) {
        try {
            RestClient client = restClientBuilder.build();

            BrevoRequest request = new BrevoRequest(
                    new Sender(from, "PronostiCup"),
                    List.of(new Recipient(to, null)),
                    subject,
                    body,
                    attachments.stream()
                            .map(a -> new Attachment(
                                    a.fileName(),
                                    Base64.getEncoder().encodeToString(a.content())
                            ))
                            .toList()
            );

            client.post()
                    .uri(brevoApiUrl)
                    .header("api-key", brevoApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException e) {
            String bodyError = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            throw new RuntimeException("Error Brevo: " + bodyError, e);
        } catch (Exception e) {
            throw new RuntimeException("Error enviando email: " + e.getMessage(), e);
        }
    }

    /**
     * Email simple
     */
    public void sendSimpleEmail(String to, String subject, String body) {
        sendEmailWithAttachments(to, subject, body, List.of());
    }

    /**
     * Mantengo tu record tal cual
     */
    public record EmailAttachment(
            String fileName,
            byte[] content,
            String contentType
    ) {}

    record BrevoRequest(
            Sender sender,
            List<Recipient> to,
            String subject,
            String htmlContent,
            List<Attachment> attachment
    ) {}

    record Sender(String email, String name) {}

    record Recipient(String email, String name) {}

    record Attachment(String name, String content) {}
}