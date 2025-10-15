package com.example.interfaces.category;

import com.example.model.category.CategoryModel;

import java.util.List;

public interface CategoryRepositoryPort {
    List<CategoryModel> categoryList();
    void validateCategoryListNotEmpty();
    CategoryModel findById(Long categoryId);
    CategoryModel createCategory(CategoryModel categoryModel);
    CategoryModel updateCategory(Long categoryId, String name, Long parentId, Long depth);
    void deleteCategory(Long categoryId);
    boolean hasCategories();
    void validateCategoryNameNotExists(String name);
}
