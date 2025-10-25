package com.example.outbound.category;

import com.example.category.apimodel.CategoryApiModel;
import com.example.category.mapper.CategoryModelMapper;
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

    private final CategoryModelMapper categoryModelMapper;

    @Override
    public List<CategoryApiModel.CategoryResponse> categoryList() {
        List<CategoryModel> categoryModelList = categoryService.getAllCategories();
        return categoryModelList
                .stream()
                .map(categoryModelMapper::toApiModel)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryApiModel.CategoryResponse findById(Long categoryId) {
        CategoryModel categoryModel = categoryService.getCategoryById(categoryId);
        return categoryModelMapper.toApiModel(categoryModel);
    }

    @Override
    public CategoryApiModel.CategoryResponse createCategory(CategoryApiModel.CreateRequest createRequest) {
        CategoryModel createCategoryResult = categoryService.createCategory(categoryModelMapper.toCreateModel(createRequest));
        return categoryModelMapper.toApiModel(createCategoryResult);
    }

    @Override
    public CategoryApiModel.CategoryResponse updateCategory(Long categoryId, CategoryApiModel.UpdateRequest updateRequest) {
        CategoryModel updateCategoryResult = categoryService.updateCategory(categoryId,categoryModelMapper.toUpdateModel(updateRequest));
        return categoryModelMapper.toApiModel(updateCategoryResult);
    }

    @Override
    public void deleteCategory(Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }

}
