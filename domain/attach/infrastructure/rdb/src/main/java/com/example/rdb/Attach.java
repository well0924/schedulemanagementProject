package com.example.rdb;

import com.example.jpa.config.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attach extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String thumbnailFilePath;

    private String originFileName;

    private String storedFileName;

    private Long fileSize;

    private String filePath;
    //연관관계
    private Long scheduledId;

    private Boolean isDeletedAttach = false;

    public void update(String originFileName, String storedFileName, Long fileSize, String filePath, String thumbnailFilePath) {
        this.originFileName = originFileName;
        this.storedFileName = storedFileName;
        this.thumbnailFilePath = thumbnailFilePath;
        this.fileSize = fileSize;
        this.filePath = filePath;
    }

    public void isDeletedAttach() {
        this.isDeletedAttach = true;
    }
}
