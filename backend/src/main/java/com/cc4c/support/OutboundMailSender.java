package com.cc4c.support;

import com.cc4c.common.MailDeliveryException;
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

/** 通过 SMTP 发送纯文本通知，并把组装、认证及 SMTP 状态转换为消息重试所需的失败分类。 */
@Component
public final class OutboundMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    /**
     * 保存邮件发送器和发件地址；实际 SMTP 发送发生在 sendText。
     *
     * @param mailSender SMTP 邮件发送器
     * @param from 邮件发件地址
     */
    public OutboundMailSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * 创建 UTF-8 纯文本邮件，附加事件标识后发送；永久失败与可重试失败由 MailDeliveryException 区分。
     *
     * @param eventId 用于邮件头部关联的事件 ID
     * @param to 收件人地址
     * @param subject 邮件主题
     * @param body 纯文本邮件正文
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
     * 先检查各邮件发送异常，再沿外层原因链查找 SMTP 状态码。
     *
     * @param exception 用于提取 SMTP 状态的发送异常
     * @return 找到的首个非零 SMTP 状态码；未找到时为 0
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
     * 沿异常原因链读取地址拒绝或发送失败的 SMTP 返回码。
     *
     * @param exception 用于提取 SMTP 状态的发送异常
     * @return SMTP 返回码；原因链中没有对应异常时为 0
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
