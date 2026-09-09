package com.cc4c.support.messaging;

/** 表示幂等领取成功、相同代次已完成或其他工作者仍持有有效租约。 */
public enum InboxClaim {
    ACQUIRED,
    ALREADY_DONE,
    ALREADY_PROCESSING
}
