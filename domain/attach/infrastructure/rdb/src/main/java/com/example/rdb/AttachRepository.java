package com.example.rdb;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AttachRepository extends JpaRepository<Attach,Long> {
    Optional<Attach> findById(Long attachId);
    Optional<Attach> findByOriginFileName(String originFileName);
    @Query(value = "select a from Attach a where a.isDeletedAttach = false")
    List<Attach> findAllByIsDeletedAttach();
    List<Attach> findAllByScheduledId(Long scheduleId);
    List<Attach> findByIdIn(List<Long> attachIds);
    //일정등록시 파일번호에 의해서 일정번호 수정
    @Transactional
    @Modifying
    @Query(value = "update Attach a set a.scheduledId = :scheduleId where a.id IN :fileIds")
    void updateScheduleId(@Param("fileIds") List<Long> fileIds, @Param("scheduleId") Long scheduleId);
}
