package com.example.outbound.category;

import com.example.category.dto.CategoryErrorCode;
import com.example.category.exception.CategoryCustomException;
import com.example.model.category.CategoryModel;
import com.example.rdb.Category;
import com.example.rdb.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class CategoryOutConnector {

    private final CategoryRepository categoryRepository;

    public List<CategoryModel> categoryList() {
        return categoryRepository
                .findAllByIsDeletedCategory()
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    public void validateCategoryListNotEmpty() {
        if (categoryRepository.count() == 0) {  // 카테고리 테이블에 데이터가 하나도 없으면
            throw new CategoryCustomException(CategoryErrorCode.NOT_FOUND_CATEGORY);  // 예외를 발생시킴
        }
    }

    public CategoryModel findById(Long categoryId) {
        return toEntity(getCategoryById(categoryId));
    }

    public CategoryModel createCategory(CategoryModel categoryModel) {
        // 카테고리명 중복 여부 검증
        validateCategoryNameNotExists(categoryModel.getName());

        // 부모 카테고리의 깊이 계산
        Long depth = calculateCategoryDepth(categoryModel.getParentId());

        // 카테고리 저장
        Category newCategory = buildCategory(categoryModel, depth);

        return toEntity(categoryRepository.save(newCategory));
    }

    public CategoryModel updateCategory(Long categoryId,String name,Long parentId,Long depth) {
        Category category = getCategoryById(categoryId);

        if (name != null && !category.getName().equals(name)) {
            validateCategoryNameNotExists(name);
        }

        depth = calculateCategoryDepth(parentId);

        if (categoryId.equals(parentId)) {
            throw new CategoryCustomException(CategoryErrorCode.INVALID_PARENT_CATEGORY);
        }

        category.update(name,parentId,depth);

        return toEntity(categoryRepository.save(category));
    }

    public void deleteCategory(Long categoryId) {
        Category category = getCategoryById(categoryId);
        //삭제 여부 true로 변경.
        category.isDeletedCategory();
        categoryRepository.save(category);
    }

    private Category getCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryCustomException(CategoryErrorCode.NOT_FOUND_CATEGORY,categoryId));
    }

    public void validateCategoryNameNotExists(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new CategoryCustomException(CategoryErrorCode.DUPLICATED_CATEGORY_NAME,name);
        }
    }

    //카테고리 존재 여부
    public boolean hasCategories() {
        return categoryRepository.count() > 0;
    }

    private Long calculateCategoryDepth(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 1L; // 루트 카테고리의 경우 기본 깊이는 1
        }

        return categoryRepository.findById(parentId)
                .map(Category::getDepth)
                .orElseThrow(() -> new CategoryCustomException(CategoryErrorCode.NOT_FOUND_CATEGORY,parentId)) + 1;
    }

    private Category buildCategory(CategoryModel categoryModel, Long depth) {
        return Category.builder()
                .name(categoryModel.getName())
                .parentId(categoryModel.getParentId())
                .depth(depth)
                .isDeletedCategory(false)
                .build();
    }

    private CategoryModel toEntity(Category category) {
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
}
