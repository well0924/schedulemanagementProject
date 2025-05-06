package com.example.rdb;

import com.example.jpa.config.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String name;
    @Column
    private Long parentId;
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long depth = 0L;
    @Column
    private Boolean isDeletedCategory = false;

    public void update(String name,Long parentId,Long depth) {
        this.name = name;
        this.parentId = (parentId == null) ? null : parentId;
        this.depth = (depth == null) ? 1 : depth;
    }

    public void isDeletedCategory() {
        this.isDeletedCategory = true;
    }
}
