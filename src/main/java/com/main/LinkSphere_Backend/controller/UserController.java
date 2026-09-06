package com.main.LinkSphere_Backend.controller;

import com.main.LinkSphere_Backend.dto.EmailChangeRequest;
import com.main.LinkSphere_Backend.dto.UpdateUsernameRequest;
import com.main.LinkSphere_Backend.dto.VerifyOtpRequest;
import com.main.LinkSphere_Backend.security.jwt.JwtAuthenticationResponse;
import com.main.LinkSphere_Backend.sevice.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
public class UserController {
    private UserService userService;

    @PatchMapping("/username")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<JwtAuthenticationResponse> updateUsername(@RequestBody UpdateUsernameRequest request, Principal principal) {
        return ResponseEntity.ok(userService.updateUsername(principal.getName(), request.getNewUsername()));
    }

    @PostMapping("/email/request-change")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> requestEmailChange(@RequestBody EmailChangeRequest request, Principal principal) {
        userService.requestEmailChange(principal.getName(), request.getNewEmail());
        return ResponseEntity.ok(Map.of("message", "Verification code sent to your new email address."));
    }

    @PostMapping("/email/confirm-change")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> confirmEmailChange(@RequestBody VerifyOtpRequest request, Principal principal) {
        userService.confirmEmailChange(principal.getName(), request.getOtp());
        return ResponseEntity.ok(Map.of("message", "Email address updated successfully."));
    }
}