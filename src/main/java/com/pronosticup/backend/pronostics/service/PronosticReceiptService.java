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
            log.info("[RECEIPT] Inicio envío comprobante alta | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias);

            Pronostic pronosticDoc = (Pronostic) pronostic;

            String json = objectMapper.writeValueAsString(pronosticDoc);
            log.info("[RECEIPT] JSON generado correctamente | length={}", json.length());

            String encrypted = encryptionService.encrypt(json);
            log.info("[RECEIPT] Contenido cifrado correctamente | encryptedLength={}", encrypted.length());

            String txtContent = buildFileContent(encrypted, leagueName, tournament, alias);
            log.info("[RECEIPT] TXT adjunto generado | txtLength={}", txtContent.length());

            byte[] pdfContent = pronosticPdfService.generatePronosticPdf(
                    pronosticDoc,
                    "Pronóstico registrado"
            );
            log.info("[RECEIPT] PDF generado | pdfBytes={}", pdfContent.length);

            String subject = "🏆 PronostiCup | Pronóstico confirmado";
            String body = buildEmailBody(leagueName, tournament, alias);

            String txtFileName = "pronostico-encriptado-" + tournament + "-" + alias + ".txt";
            String pdfFileName = "pronostico-" + tournament + "-" + alias + ".pdf";

            log.info("[RECEIPT] Preparando envío a {} con archivos [{}] y [{}]",
                    userEmail, txtFileName, pdfFileName);

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

            log.info("[RECEIPT] Envío finalizado correctamente | email={}", userEmail);

        } catch (Exception e) {
            log.error("[RECEIPT] Error enviando comprobante de pronóstico | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias, e);
        }
    }

    /**
     * Genero el comprobante cifrado del pronóstico actualizado y lo envío por email
     * junto con un PDF legible del contenido actualizado.
     */
    public void sendUpdatedPronosticReceipt(Object pronostic, String userEmail, String leagueName, String tournament, String alias ) {
        try {
            log.info("[RECEIPT] Inicio envío comprobante actualización | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias);

            Pronostic pronosticDoc = (Pronostic) pronostic;

            String json = objectMapper.writeValueAsString(pronosticDoc);
            log.info("[RECEIPT] JSON actualización generado correctamente | length={}", json.length());

            String encrypted = encryptionService.encrypt(json);
            log.info("[RECEIPT] Contenido actualización cifrado correctamente | encryptedLength={}", encrypted.length());

            String txtContent = buildUpdatedFileContent(encrypted, leagueName, tournament, alias);
            log.info("[RECEIPT] TXT actualización generado | txtLength={}", txtContent.length());

            byte[] pdfContent = pronosticPdfService.generatePronosticPdf(
                    pronosticDoc,
                    "Pronóstico actualizado"
            );
            log.info("[RECEIPT] PDF actualización generado | pdfBytes={}", pdfContent.length);

            String subject = "✏️ PronostiCup | Pronóstico actualizado";
            String body = buildUpdatedEmailBody(leagueName, tournament, alias);

            String txtFileName = "pronostico-actualizado-encriptado-" + tournament + "-" + alias + ".txt";
            String pdfFileName = "pronostico-actualizado-" + tournament + "-" + alias + ".pdf";

            log.info("[RECEIPT] Preparando envío actualización a {} con archivos [{}] y [{}]",
                    userEmail, txtFileName, pdfFileName);

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

            log.info("[RECEIPT] Envío actualización finalizado correctamente | email={}", userEmail);

        } catch (Exception e) {
            log.error("[RECEIPT] Error enviando comprobante de actualización del pronóstico | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias, e);
        }
    }

    /**
     * Envío un correo informando de que el pronóstico ha sido aceptado
     * para participar dentro de la liga.
     */
    public void sendPronosticAcceptedEmail(String userEmail, String leagueName, String tournament, String alias) {
        try {
            log.info("[RECEIPT] Inicio envío aceptación | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias);

            String subject = "✅ PronostiCup | Aceptado en liga";
            String body = buildAcceptedEmailBody(leagueName, tournament, alias);

            emailService.sendSimpleEmail(
                    userEmail,
                    subject,
                    body
            );

            log.info("[RECEIPT] Email de aceptación enviado correctamente | email={}", userEmail);

        } catch (Exception e) {
            log.error("[RECEIPT] Error enviando email de aceptación del pronóstico | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias, e);
        }
    }

    /**
     * Envío un correo informando de que el pronóstico ha sido rechazado
     * por el propietario de la liga.
     */
    public void sendPronosticRejectedEmail(String userEmail, String leagueName, String tournament, String alias ) {
        try {
            log.info("[RECEIPT] Inicio envío rechazo | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias);

            String subject = "❌ PronostiCup | No aceptado";
            String body = buildRejectedEmailBody(leagueName, tournament, alias);

            emailService.sendSimpleEmail(
                    userEmail,
                    subject,
                    body
            );

            log.info("[RECEIPT] Email de rechazo enviado correctamente | email={}", userEmail);

        } catch (Exception e) {
            log.error("[RECEIPT] Error enviando email de rechazo del pronóstico | email={} | liga={} | torneo={} | alias={}",
                    userEmail, leagueName, tournament, alias, e);
        }
    }

    private String buildFileContent(String encrypted, String leagueName, String tournament, String alias) {
        return "PronostiCup - Comprobante cifrado de pronóstico\n" +
                "Torneo: " + tournament + "\n" +
                "Liga: " + leagueName + "\n" +
                "Alias: " + alias + "\n" +
                "Fecha: " + Instant.now() + "\n\n" +
                "=== CONTENIDO CIFRADO ===\n" +
                encrypted;
    }

    private String buildEmailBody(String leagueName, String tournament, String alias) {
        return """
        <div style="font-family: Arial, Helvetica, sans-serif; max-width: 640px; margin: 0 auto; color: #1f2937; background: #ffffff;">
            
            <div style="background: linear-gradient(135deg, #16a34a 0%%, #22c55e 100%%); padding: 24px 28px; border-radius: 16px 16px 0 0; color: white;">
                <h1 style="margin: 0; font-size: 28px;">PronostiCup 🏆</h1>
                <p style="margin: 10px 0 0 0; font-size: 15px; opacity: 0.95;">
                    Confirmación de registro del pronóstico
                </p>
            </div>

            <div style="padding: 28px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 16px 16px;">
                <p style="margin-top: 0; font-size: 16px;">
                    Tu pronóstico ha sido <strong>registrado correctamente</strong>.
                </p>

                <div style="background: #f3f4f6; border-radius: 12px; padding: 18px 20px; margin: 22px 0;">
                    <p style="margin: 0 0 10px 0;"><strong>Liga:</strong> %s</p>
                    <p style="margin: 0 0 10px 0;"><strong>Torneo:</strong> %s</p>
                    <p style="margin: 0;"><strong>Alias:</strong> %s</p>
                </div>

                <div style="background: #fff7ed; border-left: 5px solid #f59e0b; padding: 16px 18px; border-radius: 10px; margin: 22px 0;">
                    <p style="margin: 0; font-size: 15px;">
                        <strong>Importante:</strong> tu pronóstico ha quedado <strong>pendiente de revisión</strong>.
                        Para poder participar en la liga, todavía falta que el <strong>propietario de la liga lo acepte</strong>.
                    </p>
                </div>

                <p style="line-height: 1.65;">
                    En este correo encontrarás:
                </p>

                <ul style="padding-left: 20px; line-height: 1.7; margin-top: 8px;">
                    <li>Un <strong>PDF con el resumen del pronóstico</strong>.</li>
                    <li>Un <strong>fichero cifrado</strong> que sirve como comprobante y copia de seguridad.</li>
                </ul>

                <div style="background: #ecfeff; border-left: 5px solid #06b6d4; padding: 16px 18px; border-radius: 10px; margin: 24px 0;">
                    <p style="margin: 0 0 8px 0;"><strong>Recomendación:</strong></p>
                    <p style="margin: 0; line-height: 1.65;">
                        Guarda este correo. El archivo cifrado te permitirá <strong>recuperar tu pronóstico sin tener que rellenarlo de nuevo</strong> en caso de que necesites volver a enviarlo más adelante.
                    </p>
                </div>

                <p style="margin-top: 28px; font-size: 13px; color: #6b7280;">
                    Gracias por usar <strong>PronostiCup</strong>.
                </p>
            </div>
        </div>
        """.formatted(leagueName, tournament.toUpperCase(), alias);
    }

    private String buildUpdatedFileContent(String encrypted, String leagueName, String tournament, String alias) {
        return "PronostiCup - Comprobante cifrado de pronóstico actualizado\n" +
                "Torneo: " + tournament + "\n" +
                "Liga: " + leagueName + "\n" +
                "Alias: " + alias + "\n" +
                "Fecha de actualización: " + Instant.now() + "\n\n" +
                "=== CONTENIDO CIFRADO ===\n" +
                encrypted;
    }

    private String buildUpdatedEmailBody(String leagueName, String tournament, String alias) {
        return """
        <div style="font-family: Arial, Helvetica, sans-serif; max-width: 640px; margin: 0 auto; color: #1f2937; background: #ffffff;">
            
            <div style="background: linear-gradient(135deg, #d97706 0%%, #f59e0b 100%%); padding: 24px 28px; border-radius: 16px 16px 0 0; color: white;">
                <h1 style="margin: 0; font-size: 28px;">PronostiCup 🏆</h1>
                <p style="margin: 10px 0 0 0; font-size: 15px; opacity: 0.95;">
                    Pronóstico actualizado
                </p>
            </div>

            <div style="padding: 28px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 16px 16px;">
                <p style="margin-top: 0; font-size: 16px;">
                    Tu pronóstico ha sido <strong>actualizado correctamente</strong>.
                </p>

                <div style="background: #f9fafb; border-radius: 12px; padding: 18px 20px; margin: 22px 0;">
                    <p style="margin: 0 0 10px 0;"><strong>Liga:</strong> %s</p>
                    <p style="margin: 0 0 10px 0;"><strong>Torneo:</strong> %s</p>
                    <p style="margin: 0;"><strong>Alias:</strong> %s</p>
                </div>

                <p style="line-height: 1.65;">
                    En este correo se adjunta el <strong>nuevo comprobante actualizado</strong>,
                    junto con el PDF del resumen.
                </p>

                <p style="margin-top: 26px; font-size: 13px; color: #6b7280;">
                    Gracias por seguir usando <strong>PronostiCup</strong>.
                </p>
            </div>
        </div>
        """.formatted(leagueName, tournament.toUpperCase(), alias);
    }

    private String buildAcceptedEmailBody(String leagueName, String tournament, String alias) {
        return """
        <div style="font-family: Arial, Helvetica, sans-serif; max-width: 640px; margin: 0 auto; color: #1f2937; background: #ffffff;">
            
            <div style="background: linear-gradient(135deg, #16a34a 0%%, #22c55e 100%%); padding: 24px 28px; border-radius: 16px 16px 0 0; color: white;">
                <h1 style="margin: 0; font-size: 28px;">PronostiCup 🏆</h1>
                <p style="margin: 10px 0 0 0; font-size: 15px; opacity: 0.95;">
                    Pronóstico aceptado en la liga
                </p>
            </div>

            <div style="padding: 28px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 16px 16px;">
                <p style="margin-top: 0; font-size: 16px;">
                    ¡Buenas noticias! Tu pronóstico ha sido <strong>aceptado correctamente</strong>.
                </p>

                <div style="background: #f0fdf4; border-radius: 12px; padding: 18px 20px; margin: 22px 0;">
                    <p style="margin: 0 0 10px 0;"><strong>Liga:</strong> %s</p>
                    <p style="margin: 0 0 10px 0;"><strong>Torneo:</strong> %s</p>
                    <p style="margin: 0;"><strong>Alias:</strong> %s</p>
                </div>

                <p style="line-height: 1.65;">
                    Desde este momento ya puedes consultar tu pronóstico dentro del apartado
                    <strong>“Mis ligas”</strong> de la aplicación.
                </p>

                <p style="margin-top: 26px; font-size: 13px; color: #6b7280;">
                    ¡Mucha suerte en la competición! ⚽
                </p>
            </div>
        </div>
        """.formatted(leagueName, tournament.toUpperCase(), alias);
    }

    private String buildRejectedEmailBody(String leagueName, String tournament, String alias) {
        return """
        <div style="font-family: Arial, Helvetica, sans-serif; max-width: 640px; margin: 0 auto; color: #1f2937; background: #ffffff;">
            
            <div style="background: linear-gradient(135deg, #dc2626 0%%, #ef4444 100%%); padding: 24px 28px; border-radius: 16px 16px 0 0; color: white;">
                <h1 style="margin: 0; font-size: 28px;">PronostiCup 🏆</h1>
                <p style="margin: 10px 0 0 0; font-size: 15px; opacity: 0.95;">
                    Pronóstico no aceptado en la liga
                </p>
            </div>

            <div style="padding: 28px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 16px 16px;">
                <p style="margin-top: 0; font-size: 16px;">
                    Tu pronóstico <strong>no ha sido aceptado</strong> para participar en la liga.
                </p>

                <div style="background: #fef2f2; border-radius: 12px; padding: 18px 20px; margin: 22px 0;">
                    <p style="margin: 0 0 10px 0;"><strong>Liga:</strong> %s</p>
                    <p style="margin: 0 0 10px 0;"><strong>Torneo:</strong> %s</p>
                    <p style="margin: 0;"><strong>Alias:</strong> %s</p>
                </div>

                <div style="background: #fff7ed; border-left: 5px solid #f59e0b; padding: 16px 18px; border-radius: 10px; margin: 22px 0;">
                    <p style="margin: 0; line-height: 1.65;">
                        Si necesitas más información sobre el rechazo, ponte en contacto con el
                        <strong>propietario de la liga</strong>.
                    </p>
                </div>

                <div style="background: #eff6ff; border-left: 5px solid #2563eb; padding: 16px 18px; border-radius: 10px; margin: 24px 0;">
                    <p style="margin: 0 0 10px 0;"><strong>No necesitas rellenar el pronóstico otra vez.</strong></p>
                    <p style="margin: 0; line-height: 1.65;">
                        Puedes reutilizar el <strong>fichero cifrado</strong> que recibiste en el correo de confirmación
                        para volver a cargar automáticamente todo el contenido.
                    </p>
                </div>

                <h3 style="margin-top: 28px; margin-bottom: 12px; color: #111827;">Cómo volver a cargar tu pronóstico paso a paso</h3>

                <ol style="padding-left: 22px; line-height: 1.8; margin-top: 0;">
                    <li>Accede a la opción <strong>“Nuevo pronóstico”</strong> dentro de la aplicación.</li>
                    <li>Introduce el <strong>ID de la liga</strong> y el <strong>alias</strong> que quieras utilizar.</li>
                    <li>Cuando llegues a la pantalla del pronóstico, localiza la opción para <strong>pegar o cargar el contenido cifrado</strong>.</li>
                    <li>Abre el correo de confirmación que recibiste anteriormente.</li>
                    <li>Copia el contenido del <strong>fichero cifrado</strong> adjunto.</li>
                    <li>Pega ese contenido en la aplicación.</li>
                    <li>El sistema cargará automáticamente el pronóstico tal y como lo habías enviado.</li>
                    <li>Revisa los datos y vuelve a enviarlo.</li>
                </ol>

                <p style="margin-top: 24px; font-size: 13px; color: #6b7280;">
                    Gracias por usar <strong>PronostiCup</strong>.
                </p>
            </div>
        </div>
        """.formatted(leagueName, tournament.toUpperCase(), alias);
    }
}