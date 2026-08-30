package com.cc4c.identity.internal;

import com.cc4c.identity.IdentityDtos.VerificationPurpose;

record VerificationEmailRequestedV1(String recipientEmail, VerificationPurpose purpose, String verificationCode) {}
