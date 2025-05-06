package com.example.service.category;

import com.example.outbound.category.CategoryOutConnector;
import com.example.model.category.CategoryModel;
import com.example.redis.config.cachekey.CacheKey;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class CategoryService {

    private final CategoryOutConnector categoryOutConnector;


    @Cacheable(value = CacheKey.CATEGORY_KEY, key = "'allCategories'", unless = "#result == null or #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<CategoryModel> getAllCategories() {
        categoryOutConnector.validateCategoryListNotEmpty();
        return categoryOutConnector.categoryList();
    }

    @Transactional(readOnly = true)
    public CategoryModel getCategoryById(Long categoryId) {
        return categoryOutConnector.findById(categoryId);
    }

    @CacheEvict(value = CacheKey.CATEGORY_KEY, key = "'allCategories'")
    public CategoryModel createCategory(CategoryModel categoryModel) {

        // 카테고리 목록이 비어있지 않을 때만 중복 확인
        if(categoryOutConnector.hasCategories()) {
            categoryOutConnector.validateCategoryNameNotExists(categoryModel.getName());
        }
        // 부모-자식 관계 설정
        categoryModel.setChildCategory(categoryModel.getParentId(), categoryModel.getCreatedBy());

        // 카테고리 생성
        return categoryOutConnector.createCategory(categoryModel);
    }

    @CacheEvict(value = CacheKey.CATEGORY_KEY, key = "'allCategories'")
    public CategoryModel updateCategory(Long categoryId,CategoryModel categoryModel) {
        // 기존 카테고리 조회
        CategoryModel existingCategory = categoryOutConnector.findById(categoryId);
        // 카테고리 이름 중복 확인
        if (categoryModel.getName() != null && !categoryModel.getName().equals(existingCategory.getName())) {
            categoryOutConnector.validateCategoryNameNotExists(categoryModel.getName());
        }
        // 부모-자식 관계 설정
        if (categoryModel.getParentId() != null && !categoryModel.getParentId().equals(existingCategory.getParentId())) {
            existingCategory.setChildCategory(categoryModel.getParentId(), categoryModel.getUpdatedBy());
        }
        // 깊이 관계 설정
        Long newDepth = (categoryModel.getDepth() != null) ? categoryModel.getDepth() : existingCategory.getDepth();

        // 카테고리 업데이트
        return categoryOutConnector.updateCategory(
                existingCategory.getId(),
                categoryModel.getName(),
                categoryModel.getParentId(),
                newDepth
        );
    }

    @CacheEvict(value = CacheKey.CATEGORY_KEY, key = "'allCategories'")
    public void deleteCategory(Long categoryId) {
        categoryOutConnector.deleteCategory(categoryId);
    }
}
