package com.example.rdb;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category,Long> {

    //카테고리 이름 중복
    boolean existsByName(String categoryName);
    //삭제여부를 반영한 카테고리 목록
    @Query(value = "select c from Category c where c.isDeletedCategory = false")
    List<Category> findAllByIsDeletedCategory();
}
