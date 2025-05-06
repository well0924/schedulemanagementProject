package com.example.outbound.category;

import com.example.category.apimodel.CategoryApiModel;
import com.example.interfaces.category.CategoryInterfaces;
import com.example.model.category.CategoryModel;
import com.example.service.category.CategoryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class CategoryInConnector implements CategoryInterfaces {

    private final CategoryService categoryService;

    @Override
    public List<CategoryApiModel.CategoryResponse> categoryList() {
        List<CategoryModel> categoryModelList = categoryService.getAllCategories();
        return categoryModelList
                .stream()
                .map(this::toApiModel)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryApiModel.CategoryResponse findById(Long categoryId) {
        CategoryModel categoryModel = categoryService.getCategoryById(categoryId);
        return toApiModel(categoryModel);
    }

    @Override
    public CategoryApiModel.CategoryResponse createCategory(CategoryApiModel.CreateRequest createRequest) {
        CategoryModel createCategoryResult = categoryService.createCategory(toCreateModel(createRequest));
        return toApiModel(createCategoryResult);
    }

    @Override
    public CategoryApiModel.CategoryResponse updateCategory(Long categoryId, CategoryApiModel.UpdateRequest updateRequest) {
        CategoryModel updateCategoryResult = categoryService.updateCategory(categoryId,toUpdateModel(updateRequest));
        return toApiModel(updateCategoryResult);
    }

    @Override
    public void deleteCategory(Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }

    //model-> api-model
    private CategoryApiModel.CategoryResponse toApiModel(CategoryModel categoryModel) {
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
    private CategoryModel toCreateModel(CategoryApiModel.CreateRequest createRequest) {
        return CategoryModel
                .builder()
                .name(createRequest.name())
                .parentId(createRequest.parentId())
                .depth(createRequest.depth())
                .build();
    }

    //model -> api-model.updateRequest
    private CategoryModel toUpdateModel(CategoryApiModel.UpdateRequest updateRequest) {
        return CategoryModel
                .builder()
                .name(updateRequest.name())
                .depth(updateRequest.depth())
                .parentId(updateRequest.parentId())
                .build();
    }
}
