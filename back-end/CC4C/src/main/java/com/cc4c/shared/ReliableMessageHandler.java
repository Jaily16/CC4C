package com.cc4c.shared;

@FunctionalInterface
public interface ReliableMessageHandler {
    void handle(MessageEnvelope envelope, byte[] plaintext);

    default void expired(MessageEnvelope envelope, byte[] plaintext) {
    }

    default void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {
    }
}
