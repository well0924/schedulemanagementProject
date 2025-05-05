package com.example.schedules;

import com.example.apimodel.ScheduleApiModel;
import com.example.enumerate.schedules.DeleteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static com.example.apimodel.ScheduleApiModel.responseSchedule;
import static com.example.apimodel.ScheduleApiModel.requestSchedule;
import static com.example.apimodel.ScheduleApiModel.updateSchedule;

import java.io.IOException;
import java.util.List;

public interface ScheduleServiceConnector {

    List<responseSchedule> findAll() throws IOException;

    List<responseSchedule> findAllDeletedSchedules();

    Page<responseSchedule> getSchedulesByUserId(String userId, Pageable pageable) throws IOException;

    Page<responseSchedule> getSchedulesByCategoryName(String categoryId,Pageable pageable) throws IOException;

    ScheduleApiModel.responseSchedule findById(Long scheduleId) throws IOException;

    ScheduleApiModel.responseSchedule saveSchedule(requestSchedule requestSchedule) throws IOException;

    ScheduleApiModel.responseSchedule updateSchedule(Long scheduleId,updateSchedule updateSchedule) throws IOException;

    void deleteSchedule(Long scheduleId, DeleteType deleteType);

    void deleteOldSchedules();

    void deleteSchedules(List<Long> ids);

    Page<responseSchedule> getSchedulesByStatus(String status, String userId,Pageable pageable) throws IOException;
}
