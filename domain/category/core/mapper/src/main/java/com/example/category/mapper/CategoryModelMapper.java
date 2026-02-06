package com.example.category.mapper;

import com.example.category.apimodel.CategoryApiModel;
import com.example.model.category.CategoryModel;
import org.springframework.stereotype.Component;

@Component
public class CategoryModelMapper {

    //model-> api-model
    public CategoryApiModel.CategoryResponse toApiModel(CategoryModel categoryModel) {
        return CategoryApiModel.CategoryResponse
                .builder()
                .id(categoryModel.getId())
                .depth(categoryModel.getDepth())
                .name(categoryModel.getName())
                .parentId(categoryModel.getParentId())
                .createdBy(categoryModel.getCreatedBy())
                .createdTime(categoryModel.getCreatedTime())
                .updatedBy(categoryModel.getUpdatedBy())
                .updatedTime(categoryModel.getUpdatedTime())
                .build();
    }

    //model -> api-model.createRequest
    public CategoryModel toCreateModel(CategoryApiModel.CreateRequest createRequest) {
        return CategoryModel
                .builder()
                .name(createRequest.name())
                .parentId(createRequest.parentId())
                .build();
    }

    //model -> api-model.updateRequest
    public CategoryModel toUpdateModel(CategoryApiModel.UpdateRequest updateRequest) {
        return CategoryModel
                .builder()
                .name(updateRequest.name())
                .depth(updateRequest.depth())
                .parentId(updateRequest.parentId())
                .build();
    }
}
