package com.pronosticup.backend.users.service;

import com.pronosticup.backend.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserReceiptService {

    private final EmailService emailService;

    private static final String FRONTEND_URL = "https://pronosticup-main.onrender.com";


    /**
     * Envía un correo de recuperación de contraseña al usuario.
     */
    public boolean sendForgotPasswordEmail(String userEmail, String username) {
        try {

            log.info(
                    "[USER_RECEIPT] Inicio envío recuperación contraseña | email={} | username={}",
                    userEmail,
                    username
            );

            String safeUsername =
                    username == null || username.isBlank()
                            ? "usuario"
                            : username;

            String recoveryUrl =
                    FRONTEND_URL +
                            "/reset-password?email=" +
                            URLEncoder.encode(userEmail, StandardCharsets.UTF_8) +
                            "&username=" +
                            URLEncoder.encode(safeUsername, StandardCharsets.UTF_8);

            String subject =
                    "🔐 PronostiCup | Recuperación de contraseña";

            String body =
                    buildForgotPasswordEmailBody(
                            safeUsername,
                            recoveryUrl
                    );

            emailService.sendSimpleEmail(
                    userEmail,
                    subject,
                    body
            );

            log.info(
                    "[USER_RECEIPT] Email recuperación contraseña enviado correctamente | email={}",
                    userEmail
            );

            return true;

        } catch (Exception e) {

            log.error(
                    "[USER_RECEIPT] Error enviando email recuperación contraseña | email={} | username={}",
                    userEmail,
                    username,
                    e
            );

            return false;
        }
    }

    private String buildForgotPasswordEmailBody(
            String username,
            String recoveryUrl
    ) {

        return """
        <div style="font-family: Arial, Helvetica, sans-serif; max-width: 640px; margin: 0 auto; color: #1f2937; background: #ffffff;">
            
            <div style="background: linear-gradient(135deg, #2563eb 0%%, #38bdf8 100%%); padding: 24px 28px; border-radius: 16px 16px 0 0; color: white;">
                <h1 style="margin: 0; font-size: 28px;">PronostiCup 🏆</h1>
                <p style="margin: 10px 0 0 0; font-size: 15px; opacity: 0.95;">
                    Recuperación de contraseña
                </p>
            </div>

            <div style="padding: 28px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 16px 16px;">

                <p style="margin-top: 0; font-size: 16px;">
                    Hola <strong>%s</strong>,
                </p>

                <p style="line-height: 1.65;">
                    Hemos recibido una solicitud para recuperar la contraseña de tu cuenta en
                    <strong>PronostiCup</strong>.
                </p>

                <div style="background: #eff6ff; border-left: 5px solid #2563eb; padding: 16px 18px; border-radius: 10px; margin: 22px 0;">

                    <p style="margin: 0 0 12px 0; line-height: 1.65;">
                        Pulsa el siguiente botón para consultar la información necesaria para recuperar el acceso a tu cuenta:
                    </p>

                    <a href="%s"
                       style="
                            display:inline-block;
                            background:#2563eb;
                            color:white;
                            text-decoration:none;
                            padding:12px 20px;
                            border-radius:8px;
                            font-weight:600;
                       ">
                        Recuperar contraseña
                    </a>

                </div>

                <div style="background: #fff7ed; border-left: 5px solid #f59e0b; padding: 16px 18px; border-radius: 10px; margin: 22px 0;">
                    <p style="margin: 0; line-height: 1.65;">
                        Si no has solicitado recuperar tu contraseña, puedes ignorar este mensaje.
                    </p>
                </div>

                <p style="margin-top: 26px; font-size: 13px; color: #6b7280;">
                    Gracias por usar <strong>PronostiCup</strong>.
                </p>

            </div>
        </div>
        """.formatted(
                username,
                recoveryUrl
        );
    }
}