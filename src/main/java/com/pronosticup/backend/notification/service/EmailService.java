package com.pronosticup.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class EmailService {

    private final RestClient.Builder restClientBuilder;

    @Value("${brevo.api.url}")
    private String brevoApiUrl;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${app.mail.from}")
    private String from;

    public void sendEmailWithAttachments(
            String to,
            String subject,
            String body,
            List<EmailAttachment> attachments
    ) {
        try {
            log.info("[EMAIL] Inicio sendEmailWithAttachments | to={} | subject={}", to, subject);
            log.info("[EMAIL] Config | brevoApiUrl={} | from={} | apiKeyPresent={}",
                    brevoApiUrl,
                    from,
                    brevoApiKey != null && !brevoApiKey.isBlank());

            if (attachments == null) {
                log.warn("[EMAIL] attachments viene null, lo convierto a lista vacía");
                attachments = List.of();
            }

            log.info("[EMAIL] Número de adjuntos={}", attachments.size());
            for (int i = 0; i < attachments.size(); i++) {
                EmailAttachment a = attachments.get(i);
                log.info("[EMAIL] Adjunto {} | name={} | bytes={} | contentType={}",
                        i + 1,
                        a.fileName(),
                        a.content() != null ? a.content().length : 0,
                        a.contentType());
            }

            List<Attachment> brevoAttachments = attachments.isEmpty()
                    ? null
                    : attachments.stream()
                    .map(a -> new Attachment(
                            a.fileName(),
                            Base64.getEncoder().encodeToString(a.content())
                    ))
                    .toList();

            BrevoRequest request = new BrevoRequest(
                    new Sender(from, "PronostiCup"),
                    List.of(new Recipient(to, null)),
                    subject,
                    body,
                    brevoAttachments
            );

            log.info("[EMAIL] Request Brevo construida correctamente | to={} | attachments={}",
                    to, attachments.size());

            RestClient client = restClientBuilder.build();

            client.post()
                    .uri(brevoApiUrl)
                    .header("api-key", brevoApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[EMAIL] Enviado correctamente con Brevo | to={}", to);

        } catch (RestClientResponseException e) {
            String bodyError = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.error("[EMAIL] Error Brevo HTTP | to={} | status={} | responseBody={}",
                    to, e.getStatusCode(), bodyError, e);
            throw new RuntimeException("Error Brevo: " + bodyError, e);
        } catch (Exception e) {
            log.error("[EMAIL] Error general enviando email | to={} | message={}",
                    to, e.getMessage(), e);
            throw new RuntimeException("Error enviando email: " + e.getMessage(), e);
        }
    }

    public void sendSimpleEmail(String to, String subject, String body) {
        try {
            log.info("[EMAIL] Inicio sendSimpleEmail | to={} | subject={}", to, subject);
            sendEmailWithAttachments(to, subject, body, null);
            log.info("[EMAIL] sendSimpleEmail completado correctamente | to={}", to);
        } catch (Exception e) {
            log.error("[EMAIL] Error en sendSimpleEmail | to={} | subject={}", to, subject, e);
            throw e;
        }
    }

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