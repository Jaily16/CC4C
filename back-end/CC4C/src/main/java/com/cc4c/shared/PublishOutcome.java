package com.cc4c.shared;

record PublishOutcome(boolean accepted, String errorCode) {
    static PublishOutcome confirmed() {
        return new PublishOutcome(true, null);
    }

    static PublishOutcome failed(String errorCode) {
        return new PublishOutcome(false, errorCode);
    }
}
