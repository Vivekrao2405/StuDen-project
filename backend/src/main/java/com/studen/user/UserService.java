package com.studen.user;

import com.studen.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getCurrentUser(UUID userId) {
        return UserResponse.from(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse updateCurrentUser(UUID userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        user.setFullName(request.fullName());
        user.setPhone(blankToNull(request.phone()));
        user.setProfileImageUrl(blankToNull(request.profileImageUrl()));
        return UserResponse.from(user);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
