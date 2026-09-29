package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.AuditLog;
import java.time.LocalDateTime;

public class AuditLogResponse {

    private Long id;
    private String action;
    private Long actorId;
    private String actorEmail;
    private String actorName;
    private Long targetId;
    private String targetEmail;
    private String targetName;
    private String details;
    private LocalDateTime createdAt;

    public AuditLogResponse() {
    }

    public static AuditLogResponse fromEntity(AuditLog log) {
        if (log == null) return null;
        AuditLogResponse res = new AuditLogResponse();
        res.setId(log.getId());
        res.setAction(log.getAction());
        res.setActorId(log.getActorId());
        res.setActorEmail(log.getActorEmail());
        res.setActorName(log.getActorName());
        res.setTargetId(log.getTargetId());
        res.setTargetEmail(log.getTargetEmail());
        res.setTargetName(log.getTargetName());
        res.setDetails(log.getDetails());
        res.setCreatedAt(log.getCreatedAt());
        return res;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getTargetEmail() {
        return targetEmail;
    }

    public void setTargetEmail(String targetEmail) {
        this.targetEmail = targetEmail;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
