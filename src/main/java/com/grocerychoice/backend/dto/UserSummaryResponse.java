package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;

public class UserSummaryResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private String gender;
    private String dateOfBirth;

    public UserSummaryResponse() {
    }

    public UserSummaryResponse(Long id, String fullName, String email, String phone, Role role) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public UserSummaryResponse(Long id, String fullName, String email, String phone, Role role, String gender, String dateOfBirth) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
    }

    public static UserSummaryResponse fromUser(User user) {
        if (user == null) return null;
        UserSummaryResponse response = new UserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole()
        );
        response.setGender(user.getGender());
        response.setDateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null);
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
}
