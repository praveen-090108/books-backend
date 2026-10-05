package com.intelliatech.app.controller;
import com.intelliatech.app.dto.request.UserRequest; import com.intelliatech.app.dto.response.UserResponse; import com.intelliatech.app.service.AppUserService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.data.domain.*; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/settings/users") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
 private final AppUserService service;
 @GetMapping public Page<UserResponse> list(String search, Pageable pageable){ return service.list(search,pageable); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserResponse create(@Valid @RequestBody UserRequest request){ return service.create(request); }
 @PutMapping("/{id}") public UserResponse update(@PathVariable Long id,@Valid @RequestBody UserRequest request){ return service.update(id,request); }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){ service.delete(id); }
}
