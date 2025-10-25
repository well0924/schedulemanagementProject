package com.example.category.mapper;

import com.example.model.category.CategoryModel;
import com.example.rdb.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryEntityMapper {

    public CategoryModel toEntity(Category category) {
        return CategoryModel
                .builder()
                .id(category.getId())
                .parentId(category.getParentId())
                .depth(category.getDepth())
                .name(category.getName())
                .createdBy(category.getCreatedBy())
                .createdTime(category.getCreatedTime())
                .updatedBy(category.getUpdatedBy())
                .updatedTime(category.getCreatedTime())
                .build();
    }

    public Category buildCategory(CategoryModel categoryModel, Long depth) {
        return Category.builder()
                .name(categoryModel.getName())
                .parentId(categoryModel.getParentId())
                .depth(depth)
                .isDeletedCategory(false)
                .build();
    }
}
