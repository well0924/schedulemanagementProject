package com.example.category.apimodel;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDateTime;

public class CategoryApiModel {
    @Builder
    public record CreateRequest(
            @NotBlank(message = "카테고리명을 입력해야 합니다.")
            String name,
            Long parentId) {}

    @Builder
    public record UpdateRequest(String name,Long parentId,Long depth) {}

    @Builder
    public record CategoryResponse(
            Long id,
            String name,
            Long parentId,
            Long depth,
            String createdBy,
            String updatedBy,
            LocalDateTime createdTime,
            LocalDateTime updatedTime
    ) {}
}
