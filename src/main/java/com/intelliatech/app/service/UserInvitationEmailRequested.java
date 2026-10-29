package com.intelliatech.app.service;
public record UserInvitationEmailRequested(Long invitationId,String recipient,String recipientName,String organizationName,String role,String token) {}
