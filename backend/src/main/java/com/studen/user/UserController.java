package com.studen.user;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getCurrentUser(principal.getId());
    }

    @PutMapping("/me")
    public UserResponse updateCurrentUser(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateCurrentUser(principal.getId(), request);
    }

    @PostMapping("/me/profile-image")
    public UserResponse uploadProfileImage(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        return userService.uploadProfileImage(principal.getId(), file);
    }

    @DeleteMapping("/me/profile-image")
    public UserResponse removeProfileImage(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.removeProfileImage(principal.getId());
    }
}
