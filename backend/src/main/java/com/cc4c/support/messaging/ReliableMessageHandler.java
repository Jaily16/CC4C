package com.cc4c.support.messaging;

/**
 * ReliableMessageHandler 定义领域能力的最小接口，供模块之间进行显式协作。
 */
@FunctionalInterface
public interface ReliableMessageHandler {
    /**
     * 处理 ReliableMessageHandler 的输入或消息，并沿用既有幂等、确认与失败恢复策略。
     *
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     */
    void handle(MessageEnvelope envelope, byte[] plaintext);

    /**
     * 执行 ReliableMessageHandler 中的 expired 职责，并保持既有权限、事务与副作用边界。
     *
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     */
    default void expired(MessageEnvelope envelope, byte[] plaintext) {}

    /**
     * 执行 ReliableMessageHandler 中的 dead 职责，并保持既有权限、事务与副作用边界。
     *
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     */
    default void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {}
}
