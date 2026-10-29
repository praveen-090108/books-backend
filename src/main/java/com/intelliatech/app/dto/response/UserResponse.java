package com.intelliatech.app.dto.response;
import java.time.LocalDateTime;
import java.util.List;
public record UserResponse(Long id, Long resourceId, String name, String email, String phone, String designation,
                           Long managerUserId, String managerName,
                           String role, String roleLabel, String status, List<String> access,
                           List<String> permissions, boolean passwordConfigured, boolean platformAdmin,
                           LocalDateTime createdAt, LocalDateTime updatedAt,
                           String invitationDeliveryStatus, LocalDateTime invitationSentAt,
                           LocalDateTime invitationAcceptedAt, LocalDateTime invitationExpiresAt) {}
