package com.example.interfaces.category;

import com.example.category.apimodel.CategoryApiModel;

import java.util.List;

public interface CategoryInterfaces {

    List<CategoryApiModel.CategoryResponse> categoryList();

    CategoryApiModel.CategoryResponse findById(Long categoryId);

    CategoryApiModel.CategoryResponse createCategory(CategoryApiModel.CreateRequest createRequest);

    CategoryApiModel.CategoryResponse updateCategory(Long categoryId,CategoryApiModel.UpdateRequest updateRequest);

    void deleteCategory(Long categoryId);
}
