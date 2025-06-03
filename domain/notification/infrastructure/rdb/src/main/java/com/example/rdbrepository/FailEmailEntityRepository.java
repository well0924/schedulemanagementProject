package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FailEmailEntityRepository extends JpaRepository<FailEmailEntity,Long> {

    List<FailEmailEntity> findByResolvedFalse();
}
