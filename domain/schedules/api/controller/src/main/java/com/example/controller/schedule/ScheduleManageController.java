package com.example.controller.schedule;

import com.example.apimodel.schedule.ScheduleApiModel;
import com.example.enumerate.schedules.DeleteType;
import com.example.enumerate.schedules.PROGRESS_STATUS;
import com.example.enumerate.schedules.RepeatUpdateType;
import com.example.inbound.schedules.ScheduleServiceConnector;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/schedule")
public class ScheduleManageController {

    private final ScheduleServiceConnector scheduleServiceConnector;

    @GetMapping("/")
    public List<ScheduleApiModel.responseSchedule> findAll() throws IOException {
        return scheduleServiceConnector.findAll();
    }

    @GetMapping("/deleted")
    public List<ScheduleApiModel.responseSchedule> deletedSchedulesAll() {
        return scheduleServiceConnector.findAllDeletedSchedules();
    }

    @GetMapping("/user")
    public Page<ScheduleApiModel.responseSchedule> findAllByUserId(@PageableDefault Pageable pageable) throws IOException {
        return scheduleServiceConnector.getSchedulesByUserId(pageable);

    }

    @GetMapping("/category/{category-name}")
    public Page<ScheduleApiModel.responseSchedule> findAllByCategoryId(@PathVariable("category-name")String categoryName, Pageable pageable) throws IOException {
        return scheduleServiceConnector.getSchedulesByCategoryName(categoryName,pageable);
    }

    @GetMapping("/status")
    public Page<ScheduleApiModel.responseSchedule> findAllByPRGRESS_STATUS(@RequestParam("status") String progressStatus,
                                                                           @PageableDefault(sort = "id",direction = Sort.Direction.DESC) Pageable pageable) throws IOException {
        return scheduleServiceConnector.getSchedulesByStatus(progressStatus,pageable);
    }

    @GetMapping("/today")
    public List<ScheduleApiModel.responseSchedule> findByTodaySchedules() {
        return scheduleServiceConnector.findByTodaySchedule();
    }

    @GetMapping("/{id}")
    public ScheduleApiModel.responseSchedule findById(@PathVariable("id")Long scheduleId) throws IOException {
        System.out.println(scheduleId);
        return scheduleServiceConnector.findById(scheduleId);
    }

    @PostMapping("/")
    public ScheduleApiModel.responseSchedule createSchedule(@Validated @RequestBody ScheduleApiModel.requestSchedule requestSchedule) throws IOException {
        return scheduleServiceConnector.saveSchedule(requestSchedule);
    }

    @PatchMapping("/{id}")
    public ScheduleApiModel.responseSchedule updateSchedule(@PathVariable("id")Long scheduleId,
                                                            @RequestParam(name = "type", defaultValue = "SINGLE") RepeatUpdateType repeatUpdateType,
                                                            @Validated @RequestBody ScheduleApiModel.updateSchedule updateSchedule) throws IOException {
        return scheduleServiceConnector.updateSchedule(scheduleId,updateSchedule,repeatUpdateType);
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
