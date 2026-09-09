package com.cc4c.support.messaging;

/** 定义已解密消息的业务处理回调；幂等领取和 AMQP 确认由外层处理器负责。 */
@FunctionalInterface
public interface ReliableMessageHandler {
    /**
     * 处理有效期内且已取得幂等租约的明文载荷，失败通过异常交给处理器分类。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param plaintext 解密后的敏感载荷字节，不得记录
     */
    void handle(MessageEnvelope envelope, byte[] plaintext);

    /**
     * 处理消息过期时的业务清理；默认无操作。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param plaintext 解密后的敏感载荷字节，不得记录
     */
    default void expired(MessageEnvelope envelope, byte[] plaintext) {}

    /**
     * 处理消息终止时的业务清理；默认无操作。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param plaintext 解密后的敏感载荷字节，不得记录
     * @param errorCode 不含敏感正文的失败分类码
     */
    default void dead(MessageEnvelope envelope, byte[] plaintext, String errorCode) {}
}
