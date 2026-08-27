package com.cc4c.community.api;

public record BlogSnapshot(long blogId, long writerId, String title, int state) {
}
