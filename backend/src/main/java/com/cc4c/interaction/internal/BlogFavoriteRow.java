package com.cc4c.interaction.internal;

import java.util.Date;
import lombok.Data;

@Data
class BlogFavoriteRow {
    private Long blogId;
    private Long writerId;
    private String title;
    private Date publishTime;
    private Integer click;
    private Integer state;
}
