package com.cc4c.identity.internal;

import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class VerificationCodeService {
    private final EmailSender emailSender;
    private final String from;
    private final SecureRandom random = new SecureRandom();

    VerificationCodeService(EmailSender emailSender, @Value("${spring.mail.username}") String from) {
        this.emailSender = emailSender;
        this.from = from;
    }

    public String send(String recipient) {
        String code = "%04d".formatted(random.nextInt(10_000));
        if (!emailSender.send(code, from, recipient)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, BusinessCode.INTERNAL_ERROR,
                    "Fail to Send Email!");
        }
        return code;
    }
}
