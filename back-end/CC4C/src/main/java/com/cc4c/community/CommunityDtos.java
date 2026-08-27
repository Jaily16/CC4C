package com.cc4c.community;

import com.cc4c.shared.IntValues;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Date;
import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {
    }

    public record BlogSubmitRequest(
            @NotBlank @Size(max = 75) String title,
            @NotBlank String content,
            @NotEmpty List<@Positive Integer> languageList
    ) {
    }

    public record BlogDraftRequest(
            @NotBlank String content
    ) {
    }

    public record BlogResponse(
            String blogId,
            String writerId,
            String title,
            String content,
            Date publishTime,
            Integer click,
            Integer state,
            List<Integer> languageList
    ) {
    }
}
