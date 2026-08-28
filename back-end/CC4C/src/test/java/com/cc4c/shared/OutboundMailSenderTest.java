package com.cc4c.shared;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboundMailSenderTest {

    @Test
    void smtpFourHundredIsTransientAndFiveHundredIsPermanent() throws Exception {
        MailDeliveryException transientFailure = sendFailure(450);
        assertFalse(transientFailure.permanent());
        assertEquals("MAIL_SMTP_TRANSIENT", transientFailure.errorCode());

        MailDeliveryException permanentFailure = sendFailure(550);
        assertTrue(permanentFailure.permanent());
        assertEquals("MAIL_SMTP_PERMANENT", permanentFailure.errorCode());
    }

    private MailDeliveryException sendFailure(int status) throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenReturn(
                new MimeMessage(Session.getInstance(new Properties())));
        SMTPAddressFailedException smtpFailure = new SMTPAddressFailedException(
                new InternetAddress("recipient@example.com"), "RCPT TO", status, "classified");
        doThrow(new MailSendException("send failed", smtpFailure))
                .when(javaMailSender).send(any(MimeMessage.class));
        OutboundMailSender sender = new OutboundMailSender(javaMailSender, "sender@example.com");

        return assertThrows(MailDeliveryException.class, () -> sender.sendText(
                "event-id", "recipient@example.com", "subject", "body"));
    }
}
