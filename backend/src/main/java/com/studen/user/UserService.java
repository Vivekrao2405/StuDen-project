package com.studen.user;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.storage.ImageValidator;
import com.studen.storage.MediaStorageService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ImageValidator imageValidator;
    private final MediaStorageService mediaStorageService;

    public UserService(UserRepository userRepository, ImageValidator imageValidator,
            MediaStorageService mediaStorageService) {
        this.userRepository = userRepository;
        this.imageValidator = imageValidator;
        this.mediaStorageService = mediaStorageService;
    }

    public UserResponse getCurrentUser(UUID userId) {
        return UserResponse.from(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse updateCurrentUser(UUID userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        user.setFullName(request.fullName());
        user.setPhone(blankToNull(request.phone()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse uploadProfileImage(UUID userId, MultipartFile file) {
        imageValidator.validate(file);
        User user = findUserOrThrow(userId);

        String secureUrl = mediaStorageService.upload(profileImagePublicId(userId), file);
        user.setProfileImageUrl(secureUrl);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse removeProfileImage(UUID userId) {
        User user = findUserOrThrow(userId);

        if (user.getProfileImageUrl() != null) {
            mediaStorageService.delete(profileImagePublicId(userId));
            user.setProfileImageUrl(null);
        }

        return UserResponse.from(user);
    }

    private String profileImagePublicId(UUID userId) {
        return "studen/users/" + userId + "/profile";
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
