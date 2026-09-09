package com.cc4c.support.messaging;

/** 定义验证码申请、博客提交和博客审核三个带 v1 版本的事件类型常量。 */
public final class AsyncEventTypes {
    public static final String VERIFICATION_EMAIL_REQUESTED = "identity.verification-email.requested.v1";
    public static final String BLOG_SUBMITTED = "community.blog.submitted.v1";
    public static final String BLOG_REVIEWED = "community.blog.reviewed.v1";

    /** 禁止实例化事件类型常量容器。 */
    private AsyncEventTypes() {}
}
