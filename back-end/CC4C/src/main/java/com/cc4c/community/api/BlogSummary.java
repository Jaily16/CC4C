package com.cc4c.community.api;

import java.util.Date;

public record BlogSummary(
        String blogId,
        String writerId,
        String title,
        Date publishTime,
        Integer click,
        Integer state
) {
}
