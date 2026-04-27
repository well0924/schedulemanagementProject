package com.example.model.member;

import com.example.enumerate.member.Roles;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemberModel {

    private Long id;
    private String userId;
    private String password;
    private String userEmail;
    private String userPhone;
    private String userName;
    private Roles roles;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public boolean isAdmin() {
        return Roles.ROLE_ADMIN.equals("ROLE_ADMIN") ?  true : false;
    }

    public boolean isValidEmail() {
        String emailPattern = "^[A-Za-z0-9+_.-]+@(.+)$";
        return this.userEmail != null && this.userEmail.matches(emailPattern);
    }

    public boolean isValidUserId() {
        return this.userId != null && this.userId.length() >= 5;
    }

    public boolean isValidPhoneNumber() {
        String phonePattern = "^\\+?[0-9]{10,13}$";
        return this.userPhone != null && this.userPhone.matches(phonePattern);
    }

}
