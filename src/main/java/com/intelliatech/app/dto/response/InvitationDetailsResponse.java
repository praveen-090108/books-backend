package com.intelliatech.app.dto.response;
import java.time.LocalDateTime;
public record InvitationDetailsResponse(String organizationName,String userName,String email,String role,String status,LocalDateTime expiresAt) {}
