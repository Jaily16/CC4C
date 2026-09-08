package com.cc4c.identity.internal;

import com.cc4c.identity.IdentityDtos.VerificationPurpose;

/**
 * VerificationEmailRequestedV1 以不可变结构承载身份认证数据，并保持现有字段语义。
 *
 * @param recipientEmail 调用方提供的 {@code recipientEmail} 值
 * @param purpose 调用方提供的 {@code purpose} 值
 * @param verificationCode 调用方提供的 {@code verificationCode} 值
 */
record VerificationEmailRequestedV1(String recipientEmail, VerificationPurpose purpose, String verificationCode) {}
