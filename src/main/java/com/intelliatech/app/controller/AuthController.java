package com.intelliatech.app.controller;
import com.intelliatech.app.dto.request.*; import com.intelliatech.app.dto.response.*; import com.intelliatech.app.service.AuthService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
 private final AuthService service;
 @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request){ return service.login(request); }
 @PostMapping("/forgot-password") public ForgotPasswordResponse forgot(@Valid @RequestBody ForgotPasswordRequest request){ return service.forgot(request); }
 @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@Valid @RequestBody ResetPasswordRequest request){ service.reset(request); }
}
