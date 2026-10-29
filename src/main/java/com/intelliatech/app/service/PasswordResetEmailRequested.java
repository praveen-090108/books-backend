package com.intelliatech.app.service;
public record PasswordResetEmailRequested(String recipient, String recipientName, String token) {}
