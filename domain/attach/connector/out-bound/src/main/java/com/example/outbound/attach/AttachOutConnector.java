package com.example.outbound.attach;

import com.example.attach.dto.AttachErrorCode;
import com.example.attach.exception.AttachCustomExceptionHandler;
import com.example.attach.mapper.AttachEntityMapper;
import com.example.interfaces.attach.AttachRepositoryPort;
import com.example.model.attach.AttachModel;
import com.example.rdb.Attach;
import com.example.rdb.AttachRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AttachOutConnector implements AttachRepositoryPort {

    private final AttachRepository attachRepository;

    private final AttachEntityMapper attachEntityMapper;

    public List<AttachModel> findAll() {
        List<AttachModel> attachList = attachRepository
                .findAllByIsDeletedAttach()
                .stream()
                .map(attachEntityMapper::toModel)
                .collect(Collectors.toList());

        if(attachList.isEmpty()) {
            throw new AttachCustomExceptionHandler(AttachErrorCode.NOT_FOUND_ATTACH_LIST);
        }

        return attachList;
    }
    
    //일정번호만 있는 첨부파일 목록
    public List<AttachModel> findAllByScheduleId(Long scheduledId){
        return attachRepository.findAllByScheduledId(scheduledId)
                .stream()
                .map(attachEntityMapper::toModel)
                .collect(Collectors.toList());
    }

    public List<AttachModel> findByIdIn(List<Long> attachIds) {
        return attachRepository.findByIdIn(attachIds)
                .stream()
                .map(attachEntityMapper::toModel)
                .collect(Collectors.toList());
    }

    public AttachModel findById(Long attachId) {
        return attachRepository.findById(attachId)
                .map(attachEntityMapper::toModel)
                .orElseThrow(()-> new AttachCustomExceptionHandler(AttachErrorCode.NOT_FOUND_ATTACH));
    }

    public AttachModel findByOriginFileName(String originFileName) {
        return attachRepository.findByOriginFileName(originFileName)
                .map(attachEntityMapper::toModel)
                .orElseThrow(()-> new AttachCustomExceptionHandler(AttachErrorCode.NOT_FOUND_ATTACH));
    }

    public AttachModel findByStoredFileName(String storedFileName) {
        return attachRepository.findByStoredFileName(storedFileName)
                .map(attachEntityMapper::toModel)
                .orElseThrow(()-> new AttachCustomExceptionHandler(AttachErrorCode.NOT_FOUND_ATTACH));
    }

    public AttachModel createAttach(AttachModel attachModel) {

        Attach attach = Attach
                .builder()
                .scheduledId(attachModel.getScheduledId())
                .originFileName(attachModel.getOriginFileName())
                .storedFileName(attachModel.getStoredFileName())
                .thumbnailFilePath(attachModel.getThumbnailFilePath())
                .fileSize(attachModel.getFileSize())
                .filePath(attachModel.getFilePath())
                .isDeletedAttach(false)
                .build();

        return attachEntityMapper.toModel(attachRepository.save(attach));
    }

    public AttachModel updateAttach(Long attachId,AttachModel attachModel) {
        Attach attach = findByOne(attachId);
        attach.update(attachModel.getOriginFileName(), attachModel.getStoredFileName(), attachModel.getFileSize(), attachModel.getFilePath(), attachModel.getThumbnailFilePath());
        return attachEntityMapper.toModel(attach);
    }

    public void deleteAttach(Long attachId) {
        Attach attach = findByOne(attachId);
        attach.isDeletedAttach();
        attachRepository.save(attach);
    }

    //일정등록시 파일번호에 의해서 일정번호 수정
    public void updateScheduleId(List<Long> fileIds, Long scheduleId) {
        attachRepository.updateScheduleId(fileIds,scheduleId);
    }

    private Attach findByOne(Long attachId) {
        return attachRepository
                .findById(attachId)
                .orElseThrow(()->new AttachCustomExceptionHandler(AttachErrorCode.NOT_FOUND_ATTACH));
    }
}
