package com.example.rdb.member;

import com.example.enumerate.member.Roles;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.stereotype.Indexed;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String userId;
    @Column
    private String password;
    @Column
    private String userPhone;
    @Column
    private String userEmail;
    @Column
    private String userName;
    @Enumerated(EnumType.STRING)
    private Roles roles;

    private boolean isDeletedUser;

    @CreatedBy
    private String createdBy; // 생성자

    @LastModifiedBy
    private String updatedBy; // 수정자

    @CreatedDate
    private LocalDateTime createdTime;

    @LastModifiedDate
    private LocalDateTime updatedTime;

    public void update(String userId,String userEmail, String userPhone) {
        if(userId !=null) this.userId = userId;
        if(userEmail != null) this.userEmail = userEmail;
        if(userPhone != null) this.userPhone = userPhone;
    }
}
