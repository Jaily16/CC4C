package com.cc4c.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/** CommunityDtos 表示身份、业务或交互边界上的数据传输结构。 */
public final class CommunityDtos {
    private CommunityDtos() {}

    /** BlogSubmitRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record BlogSubmitRequest(
            @NotBlank @Size(max = 75) String title,
            @NotBlank String content,
            @NotEmpty List<@Positive Integer> languageList) {}

    /** BlogDraftRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record BlogDraftRequest(@NotBlank String content) {}

    /** BlogResponse 表示身份、业务或交互边界上的数据传输结构。 */
    public record BlogResponse(
            String blogId,
            String writerId,
            String title,
            String content,
            Date publishTime,
            Integer click,
            Integer state,
            List<Integer> languageList) {}
}
