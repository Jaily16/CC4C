package com.cc4c.shared;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * OutboundMailSender 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public final class OutboundMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    /**
     * 创建 OutboundMailSender 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param mailSender 调用方提供的 {@code mailSender} 值
     * @param from 调用方提供的 {@code from} 值
     */
    public OutboundMailSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * 按既有可靠消息或邮件协议发送数据，并保留调用方可观察的失败语义。
     *
     * @param eventId 目标对象的稳定标识
     * @param to 调用方提供的 {@code to} 值
     * @param subject 调用方提供的 {@code subject} 值
     * @param body 调用方提供的 {@code body} 值
     */
    public void sendText(String eventId, String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
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
                    permanent ? "MAIL_SMTP_PERMANENT" : "MAIL_SMTP_TRANSIENT", permanent, exception);
        } catch (MailException exception) {
            throw new MailDeliveryException("MAIL_TRANSIENT", false, exception);
        }
    }

    /**
     * 执行 OutboundMailSender 中的 smtpStatus 职责，并保持既有权限、事务与副作用边界。
     *
     * @param exception 调用方提供的 {@code exception} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private int smtpStatus(MailSendException exception) {
        for (Exception nested : exception.getMessageExceptions()) {
            int status = smtpStatus(nested);
            if (status != 0) {
                return status;
            }
        }
        return smtpStatus(exception.getCause());
    }

    /**
     * 执行 OutboundMailSender 中的 smtpStatus 职责，并保持既有权限、事务与副作用边界。
     *
     * @param exception 调用方提供的 {@code exception} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
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
