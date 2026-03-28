package com.pronosticup.backend.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    /**
     * Envío un email con varios adjuntos binarios.
     */
    public void sendEmailWithAttachments(
            String to,
            String subject,
            String body,
            List<EmailAttachment> attachments
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            for (EmailAttachment attachment : attachments) {
                helper.addAttachment(
                        attachment.fileName(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType()
                );
            }

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Error enviando email a " + to + ": " + e.getMessage(), e);
        }
    }

    /**
     * Envío un email con un único adjunto tipo texto.
     */
    public void sendEmailWithAttachment(
            String to,
            String subject,
            String body,
            String fileName,
            String fileContent
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            helper.addAttachment(
                    fileName,
                    new ByteArrayResource(fileContent.getBytes())
            );

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Error enviando email", e);
        }
    }

    /**
     * Envío un email simple sin adjuntos.
     */
    public void sendSimpleEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Error enviando email a " + to + ": " + e.getMessage(), e);
        }
    }

    /**
     * Represento un adjunto de email con su nombre, contenido y tipo MIME.
     */
    public record EmailAttachment(
            String fileName,
            byte[] content,
            String contentType
    ) {}
}