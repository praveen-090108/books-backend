package com.intelliatech.app.service;
public record VerificationEmailRequested(String recipient, String recipientName, String token) {}
