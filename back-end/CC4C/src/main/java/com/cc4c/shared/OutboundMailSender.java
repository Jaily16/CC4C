package com.cc4c.shared;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;

import java.nio.charset.StandardCharsets;

@Component
public final class OutboundMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public OutboundMailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendText(String eventId, String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            message.setHeader("Message-ID", "<" + eventId + "@cc4c.local>");
            message.setHeader("X-CC4C-Event-Id", eventId);
            mailSender.send(message);
        } catch (MailPreparationException | MessagingException | IllegalArgumentException exception) {
            throw new MailDeliveryException("MAIL_PERMANENT", true, exception);
        } catch (MailAuthenticationException exception) {
            throw new MailDeliveryException("MAIL_AUTHENTICATION", false, exception);
        } catch (MailSendException exception) {
            int status = smtpStatus(exception);
            boolean permanent = status >= 500 && status < 600;
            throw new MailDeliveryException(
                    permanent ? "MAIL_SMTP_PERMANENT" : "MAIL_SMTP_TRANSIENT",
                    permanent,
                    exception);
        } catch (MailException exception) {
            throw new MailDeliveryException("MAIL_TRANSIENT", false, exception);
        }
    }

    private int smtpStatus(MailSendException exception) {
        for (Exception nested : exception.getMessageExceptions()) {
            int status = smtpStatus(nested);
            if (status != 0) {
                return status;
            }
        }
        return smtpStatus(exception.getCause());
    }

    private int smtpStatus(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SMTPAddressFailedException addressFailure) {
                return addressFailure.getReturnCode();
            }
            if (current instanceof SMTPSendFailedException sendFailure) {
                return sendFailure.getReturnCode();
            }
            current = current.getCause();
        }
        return 0;
    }
}
