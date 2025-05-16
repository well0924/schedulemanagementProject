package com.example.controller.schedule;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.inbound.schedules.ScheduleServiceConnectorImpl;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/schedule")
public class ScheduleManageController {

    private final ScheduleServiceConnectorImpl scheduleServiceConnector;

    @GetMapping("/")
    public List<ScheduleApiModel.responseSchedule> findAll() {
        return scheduleServiceConnector.findAll();
    }

    @GetMapping("/deleted")
    public List<ScheduleApiModel.responseSchedule> deletedSchedulesAll() {
        return scheduleServiceConnector.findAllDeletedSchedules();
    }

    @GetMapping("/user/{id}")
    public Page<ScheduleApiModel.responseSchedule> findAllByUserId(@PathVariable("id")String userId, @PageableDefault Pageable pageable) {
        return scheduleServiceConnector.getSchedulesByUserId(userId,pageable);
    }

    @GetMapping("/category/{category-name}")
    public Page<ScheduleApiModel.responseSchedule> findAllByCategoryId(@PathVariable("category-name")String categoryName, Pageable pageable) {
        return scheduleServiceConnector.getSchedulesByCategoryName(categoryName,pageable);
    }

    @GetMapping("/status")
    public Page<ScheduleApiModel.responseSchedule> findAllByPRGRESS_STATUS(@RequestParam("status") String progressStatus,
                                                                           @RequestParam("userId") String userId,
                                                                           @PageableDefault(sort = "id",direction = Sort.Direction.DESC) Pageable pageable) {
        return scheduleServiceConnector.getSchedulesByStatus(progressStatus,userId,pageable);
    }

    @GetMapping("/today/{id}")
    public List<ScheduleApiModel.responseSchedule> findByTodaySchedules(@PathVariable("id")Long userId) {
        return scheduleServiceConnector.findByTodaySchedule(userId);
    }

    @GetMapping("/{id}")
    public ScheduleApiModel.responseSchedule findById(@PathVariable("id")Long scheduleId) {
        System.out.println(scheduleId);
        return scheduleServiceConnector.findById(scheduleId);
    }

    @PostMapping("/")
    public ScheduleApiModel.responseSchedule createSchedule(@Validated @RequestBody ScheduleApiModel.requestSchedule requestSchedule) {
        return scheduleServiceConnector.saveSchedule(requestSchedule);
    }

    @PatchMapping("/{id}")
    public ScheduleApiModel.responseSchedule updateSchedule(@PathVariable("id")Long scheduleId,@Validated @RequestBody ScheduleApiModel.updateSchedule updateSchedule) {
        return scheduleServiceConnector.updateSchedule(scheduleId,updateSchedule);
    }

    @PatchMapping("/status/{id}")
    public ScheduleApiModel.responseScheduleStatus updateStatus(@PathVariable("id")Long id, @RequestBody PROGRESS_STATUS status) {
       return scheduleServiceConnector.updateScheduleStatus(id,status);
    }

    @PostMapping("/{id}")
    public String deleteSchedule(@PathVariable("id")Long scheduleId,
                                 @RequestParam(name = "type", defaultValue = "SINGLE") DeleteType deleteType) {
        scheduleServiceConnector.deleteSchedule(scheduleId,deleteType);
        return "Delete Schedule.";
    }

    @PostMapping("/bulk-delete")
    public String deleteMultipleSchedules(@RequestBody List<Long> scheduleIds) {
        scheduleServiceConnector.deleteSchedules(scheduleIds);
        return "Bulk delete completed.";
    }

    @DeleteMapping("/old-schedules")
    public void deleteAllSchedule() {
        scheduleServiceConnector.deleteOldSchedules();
    }
}
