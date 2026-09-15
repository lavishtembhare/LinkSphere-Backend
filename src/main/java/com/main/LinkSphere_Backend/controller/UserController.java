package com.main.LinkSphere_Backend.controller;

import com.main.LinkSphere_Backend.dto.DeleteAccountRequest;
import com.main.LinkSphere_Backend.dto.EmailChangeRequest;
import com.main.LinkSphere_Backend.dto.UpdateUsernameRequest;
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

    @PatchMapping("/email")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> updateEmail(@RequestBody EmailChangeRequest request, Principal principal) {
        userService.updateEmail(principal.getName(), request.getNewEmail(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Email address updated successfully."));
    }

    @PostMapping("/delete-account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> deleteAccount(@RequestBody DeleteAccountRequest request, Principal principal) {
        userService.deleteAccount(principal.getName(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Your account and all associated data have been permanently deleted."));
    }
}