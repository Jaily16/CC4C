package com.cc4c.support.messaging;

import com.cc4c.dto.IdentityDtos.VerificationPurpose;

/**
 * 验证码邮件的敏感明文载荷，只在加密前或解密后用于发信和校验激活。
 *
 * @param recipientEmail 通知收件邮箱，不得记录
 * @param purpose 验证码的注册或密码重置用途
 * @param verificationCode 明文六位验证码，不得记录
 */
public record VerificationEmailRequestedV1(
        String recipientEmail, VerificationPurpose purpose, String verificationCode) {}
