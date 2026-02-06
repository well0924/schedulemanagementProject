package com.example.model.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryModel {

    private Long id;
    private String name;
    private Long parentId;
    private Long depth;
    private Boolean isDeletedCategory;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public void setChildCategory(Long parentId, String createdBy) {
        this.parentId = parentId;
        this.depth = (parentId == null) ? 1L : getDepth() + 1;  // 부모 카테고리에서 1 깊이 추가
        this.createdBy = createdBy;
        this.createdTime = LocalDateTime.now();
    }

    public Long getDepth() {
        return (depth == null) ? 0L : depth;
    }

}
