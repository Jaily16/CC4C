package com.cc4c.identity.internal;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {
    private final JavaMailSender mailSender;

    EmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean send(String code, String from, String to) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setSubject("Verification Code");
            message.setText("Your verification code is " + code);
            message.setFrom(from);
            message.setTo(to);
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            return false;
        }
    }
}
