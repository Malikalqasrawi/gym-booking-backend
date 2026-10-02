package com.mycompany.gymbooking.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends emails over SMTP with the spring.mail.* settings (Gmail by default). Each email has a
 * plain-text and an HTML version, and comes from the account in spring.mail.username, shown with
 * the name in app.mail.from-name.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "email")
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpMailTransport(JavaMailSender mailSender,
                             @Value("${spring.mail.username:}") String fromAddress,
                             @Value("${spring.mail.password:}") String password,
                             @Value("${app.mail.from-name}") String fromName) {
        // Fail at startup rather than on the first email.
        if (fromAddress.isBlank() || password.isBlank()) {
            throw new IllegalStateException("app.notifications.mode=email needs spring.mail.username and "
                    + "spring.mail.password in local.properties (for Gmail: your address and an app password).");
        }
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void send(String recipient, String subject, String text, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");   // true: text and HTML parts
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, html);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Could not build the email: " + e.getMessage(), e);
        }
        mailSender.send(message);
    }
}
