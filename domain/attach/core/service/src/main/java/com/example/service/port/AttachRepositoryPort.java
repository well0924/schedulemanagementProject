package com.example.service.port;

import com.example.model.attach.AttachModel;

import java.util.List;

public interface AttachRepositoryPort {

    public List<AttachModel> findAll();
    public List<AttachModel> findAllByScheduleId(Long scheduledId);
    public List<AttachModel> findByIdIn(List<Long> attachIds);
    public AttachModel findById(Long attachId);
    public AttachModel findByOriginFileName(String originFileName);
    public AttachModel findByStoredFileName(String storedFileName);
    public AttachModel createAttach(AttachModel attachModel);
    public AttachModel updateAttach(Long attachId,AttachModel attachModel);
    public void deleteAttach(Long attachId);
    public void updateScheduleId(List<Long> fileIds, Long scheduleId);

}
