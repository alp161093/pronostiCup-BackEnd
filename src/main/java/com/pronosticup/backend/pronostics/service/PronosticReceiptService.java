package com.pronosticup.backend.pronostics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pronosticup.backend.notification.service.EmailService;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import com.pronosticup.backend.security.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PronosticReceiptService {

    private final EncryptionService encryptionService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final PronosticPdfService pronosticPdfService;

    /**
     * Genero el comprobante cifrado del pronóstico y lo envío por email
     * junto con un PDF legible del contenido.
     */
    public void sendEncryptedPronosticReceipt(Object pronostic, String userEmail, String leagueName, String tournament, String alias ) {
        try {
            Pronostic pronosticDoc = (Pronostic) pronostic;

            String json = objectMapper.writeValueAsString(pronosticDoc);
            String encrypted = encryptionService.encrypt(json);

            String txtContent = buildFileContent(encrypted, leagueName, tournament, alias);
            byte[] pdfContent = pronosticPdfService.generatePronosticPdf(
                    pronosticDoc,
                    "Pronóstico registrado"
            );

            String subject = "PronostiCup - Confirmación de pronóstico";
            String body = buildEmailBody(leagueName, tournament, alias);

            String txtFileName = "pronostico-encriptado-" + tournament + "-" + alias + ".txt";
            String pdfFileName = "pronostico-" + tournament + "-" + alias + ".pdf";

            emailService.sendEmailWithAttachments(
                    userEmail,
                    subject,
                    body,
                    List.of(
                            new EmailService.EmailAttachment(
                                    txtFileName,
                                    txtContent.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                    "text/plain"
                            ),
                            new EmailService.EmailAttachment(
                                    pdfFileName,
                                    pdfContent,
                                    "application/pdf"
                            )
                    )
            );

        } catch (Exception e) {
            log.error("Error enviando comprobante de pronóstico", e);
        }
    }

    /**
     * Genero el comprobante cifrado del pronóstico actualizado y lo envío por email
     * junto con un PDF legible del contenido actualizado.
     */
    public void sendUpdatedPronosticReceipt( Object pronostic, String userEmail, String leagueName, String tournament, String alias ) {
        try {
            Pronostic pronosticDoc = (Pronostic) pronostic;

            String json = objectMapper.writeValueAsString(pronosticDoc);
            String encrypted = encryptionService.encrypt(json);

            String txtContent = buildUpdatedFileContent(encrypted, leagueName, tournament, alias);
            byte[] pdfContent = pronosticPdfService.generatePronosticPdf(
                    pronosticDoc,
                    "Pronóstico actualizado"
            );

            String subject = "PronostiCup - Pronóstico actualizado";
            String body = buildUpdatedEmailBody(leagueName, tournament, alias);

            String txtFileName = "pronostico-actualizado-encriptado-" + tournament + "-" + alias + ".txt";
            String pdfFileName = "pronostico-actualizado-" + tournament + "-" + alias + ".pdf";

            emailService.sendEmailWithAttachments(
                    userEmail,
                    subject,
                    body,
                    List.of(
                            new EmailService.EmailAttachment(
                                    txtFileName,
                                    txtContent.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                    "text/plain"
                            ),
                            new EmailService.EmailAttachment(
                                    pdfFileName,
                                    pdfContent,
                                    "application/pdf"
                            )
                    )
            );

        } catch (Exception e) {
            log.error("Error enviando comprobante de actualización del pronóstico", e);
        }
    }

    /**
     * Envío un correo informando de que el pronóstico ha sido aceptado
     * para participar dentro de la liga.
     */
    public void sendPronosticAcceptedEmail(String userEmail, String leagueName, String tournament, String alias) {
        try {
            String subject = "PronostiCup - Pronóstico aceptado en la liga";

            String body = buildAcceptedEmailBody(leagueName, tournament, alias);

            emailService.sendSimpleEmail(
                    userEmail,
                    subject,
                    body
            );

        } catch (Exception e) {
            log.error("Error enviando email de aceptación del pronóstico", e);
        }
    }

    /**
     * Envío un correo informando de que el pronóstico ha sido rechazado
     * por el propietario de la liga.
     */
    public void sendPronosticRejectedEmail(String userEmail, String leagueName, String tournament, String alias ) {
        try {
            String subject = "PronostiCup - Pronóstico rechazado en la liga";

            String body = buildRejectedEmailBody(leagueName, tournament, alias);

            emailService.sendSimpleEmail(
                    userEmail,
                    subject,
                    body
            );

        } catch (Exception e) {
            log.error("Error enviando email de rechazo del pronóstico", e);
        }
    }

    /**
     * Construyo el contenido del fichero adjunto.
     */
    private String buildFileContent(String encrypted, String leagueName, String tournament, String alias) {
        return "PronostiCup - Comprobante cifrado de pronóstico\n" +
                "Torneo: " + tournament + "\n" +
                "Liga: " + leagueName + "\n" +
                "Alias: " + alias + "\n" +
                "Fecha: " + Instant.now() + "\n\n" +
                "=== CONTENIDO CIFRADO ===\n" +
                encrypted;
    }

    /**
     * Construyo el cuerpo del email.
     */
    private String buildEmailBody(String leagueName, String tournament, String alias) {
        return "Tu pronóstico ha sido registrado correctamente.\n\n" +
                "Liga: " + leagueName + "\n" +
                "Torneo: " + tournament + "\n" +
                "Alias: " + alias + "\n\n" +
                "Se adjunta un fichero con el comprobante cifrado de tu pronóstico.\n" +
                "Este fichero puede utilizarse como verificación en el futuro.\n\n" +
                "Gracias por usar PronostiCup.";
    }

    /**
     * Construyo el contenido del fichero adjunto para una actualización.
     */
    private String buildUpdatedFileContent(String encrypted, String leagueName, String tournament, String alias) {
        return "PronostiCup - Comprobante cifrado de pronóstico actualizado\n" +
                "Torneo: " + tournament + "\n" +
                "Liga: " + leagueName + "\n" +
                "Alias: " + alias + "\n" +
                "Fecha de actualización: " + Instant.now() + "\n\n" +
                "=== CONTENIDO CIFRADO ===\n" +
                encrypted;
    }

    /**
     * Construyo el cuerpo del email para una actualización.
     */
    private String buildUpdatedEmailBody(String leagueName, String tournament, String alias) {
        return "Tu pronóstico ha sido actualizado correctamente.\n\n" +
                "Liga: " + leagueName + "\n" +
                "Torneo: " + tournament + "\n" +
                "Alias: " + alias + "\n\n" +
                "Se adjunta un fichero con el comprobante cifrado del pronóstico actualizado.\n" +
                "Este fichero puede utilizarse como verificación en el futuro.\n\n" +
                "Gracias por usar PronostiCup.";
    }

    /**
     * Construyo el cuerpo del email cuando el pronóstico ha sido aceptado.
     */
    private String buildAcceptedEmailBody(String leagueName, String tournament, String alias) {
        return "Tu pronóstico ha sido aceptado correctamente por el propietario de la liga para participar en ella.\n\n" +
                "Liga: " + leagueName + "\n" +
                "Torneo: " + tournament + "\n" +
                "Alias: " + alias + "\n\n" +
                "Desde este momento ya puedes consultar este pronóstico dentro del desplegable \"Mis ligas\" de la aplicación.\n\n" +
                "Gracias por usar PronostiCup.";
    }

    /**
     * Construyo el cuerpo del email cuando el pronóstico ha sido rechazado.
     */
    private String buildRejectedEmailBody(String leagueName, String tournament, String alias) {
        return "Tu pronóstico ha sido rechazado por el propietario de la liga para participar ella.\n\n" +
                "Liga: " + leagueName + "\n" +
                "Torneo: " + tournament + "\n" +
                "Alias: " + alias + "\n\n" +
                "Si quieres conocer el motivo, ponte en contacto con el propietario de la liga.\n\n" +
                "Si el rechazo se ha producido por equivocación, debes acceder a \"+Nuevo pronóstico\", " +
                "volver a rellenar los datos con el ID de liga y el alias que quieras utilizar y, " +
                "cuando accedas a la parte del pronóstico, copiar el texto encriptado que se te facilitó " +
                "en el correo de confirmación del pronóstico. Con ese texto se cargará automáticamente " +
                "todo el pronóstico tal y como fue enviado la primera vez.\n\n" +
                "Gracias por usar PronostiCup.";
    }
}