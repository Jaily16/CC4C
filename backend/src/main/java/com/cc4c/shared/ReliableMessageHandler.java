package com.cc4c.shared;

@FunctionalInterface
/** ReliableMessageHandler 定义领域能力的最小接口，供模块之间进行显式协作。 */
public interface ReliableMessageHandler {
    void handle(MessageEnvelope envelope, byte[] plaintext);

    default void expired(MessageEnvelope envelope, byte[] plaintext) {}

    default void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {}
}
