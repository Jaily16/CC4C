package com.cc4c.shared;

/** AsyncEventTypes 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class AsyncEventTypes {
    public static final String VERIFICATION_EMAIL_REQUESTED = "identity.verification-email.requested.v1";
    public static final String BLOG_SUBMITTED = "community.blog.submitted.v1";
    public static final String BLOG_REVIEWED = "community.blog.reviewed.v1";

    private AsyncEventTypes() {}
}
