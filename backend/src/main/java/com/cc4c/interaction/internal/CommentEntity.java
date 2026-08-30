package com.cc4c.interaction.internal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("comment")
class CommentEntity {
    @TableId(value = "comment_id", type = IdType.ASSIGN_ID)
    private Long commentId;

    private Long userId;
    private String content;
    private Date time;

    @TableField("\u0060like\u0060")
    private Integer like;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
