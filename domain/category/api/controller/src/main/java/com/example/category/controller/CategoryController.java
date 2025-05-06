package com.example.category.controller;

import com.example.category.apimodel.CategoryApiModel;
import com.example.outbound.category.CategoryInConnector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryInConnector categoryInConnector;

    @GetMapping("/")
    public ResponseEntity<List<CategoryApiModel.CategoryResponse>> categoryList() {
        List<CategoryApiModel.CategoryResponse> categoryList = categoryInConnector.categoryList();
        log.info("categoryList::"+categoryList);
        return ResponseEntity.status(HttpStatus.OK).body(categoryList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryApiModel.CategoryResponse> findById(@PathVariable("id")Long categoryId) {
        CategoryApiModel.CategoryResponse categoryResponse = categoryInConnector.findById(categoryId);
        log.info("category::"+categoryResponse);
        return ResponseEntity.status(HttpStatus.OK).body(categoryResponse);
    }

    @PostMapping("/")
    public ResponseEntity<CategoryApiModel.CategoryResponse> createCategory(@Validated @RequestBody CategoryApiModel.CreateRequest createRequest) {
        CategoryApiModel.CategoryResponse createCategoryResult = categoryInConnector.createCategory(createRequest);
        log.info("result::"+createCategoryResult);
        return  ResponseEntity.status(HttpStatus.CREATED).body(createCategoryResult);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryApiModel.CategoryResponse> updateCategory(@PathVariable("id") Long categoryId,@Validated @RequestBody CategoryApiModel.UpdateRequest updateRequest) {
        CategoryApiModel.CategoryResponse updateCategoryResult = categoryInConnector.updateCategory(categoryId,updateRequest);
        log.info("updateResult::"+updateCategoryResult);
        return ResponseEntity.status(HttpStatus.OK).body(updateCategoryResult);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("id")Long categoryId) {
        categoryInConnector.deleteCategory(categoryId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
