package com.studen.resource;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Student-facing reads/actions. myLearning() is a thin pass-through to ResourceMatchingService —
// kept as a separate service (rather than folding matching logic in here) so the matching/
// aggregation algorithm stays independently testable.
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final ResourceMatchingService matchingService;

    public ResourceService(ResourceRepository resourceRepository, StudentResourceProgressRepository progressRepository,
            UserRepository userRepository, ResourceMatchingService matchingService) {
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
    }

    @Transactional(readOnly = true)
    public MyLearningResponse myLearning(UUID userId) {
        return matchingService.myLearning(userId);
    }

    // Deliberately not gated by "is this one of my matched weak-area resources" — a student may
    // reach a published resource via a direct link/share, same as any other published content in
    // this app.
    @Transactional(readOnly = true)
    public ResourceResponse get(UUID userId, UUID resourceId) {
        Resource resource = findPublished(resourceId);
        ResourceProgressStatus status = progressRepository.findByStudentIdAndResourceId(userId, resourceId)
                .map(StudentResourceProgress::getStatus)
                .orElse(ResourceProgressStatus.NOT_STARTED);
        return ResourceResponse.from(resource, status);
    }

    @Transactional
    public ResourceProgressResponse start(UUID userId, UUID resourceId) {
        Resource resource = findPublished(resourceId);
        StudentResourceProgress progress = progressRepository.findByStudentIdAndResourceId(userId, resourceId).orElse(null);
        if (progress == null) {
            progress = new StudentResourceProgress(findUser(userId), resource, ResourceProgressStatus.IN_PROGRESS);
            progress.setStartedAt(Instant.now());
            progress = progressRepository.save(progress);
        }
        // Already IN_PROGRESS or COMPLETED — idempotent no-op, never regresses a completed resource.
        return ResourceProgressResponse.from(progress);
    }

    @Transactional
    public ResourceProgressResponse complete(UUID userId, UUID resourceId) {
        Resource resource = findPublished(resourceId);
        StudentResourceProgress progress = progressRepository.findByStudentIdAndResourceId(userId, resourceId).orElse(null);
        Instant now = Instant.now();
        if (progress == null) {
            progress = new StudentResourceProgress(findUser(userId), resource, ResourceProgressStatus.COMPLETED);
            progress.setStartedAt(now);
            progress.setCompletedAt(now);
            progress = progressRepository.save(progress);
        } else if (progress.getStatus() != ResourceProgressStatus.COMPLETED) {
            if (progress.getStartedAt() == null) {
                progress.setStartedAt(now);
            }
            progress.setStatus(ResourceProgressStatus.COMPLETED);
            progress.setCompletedAt(now);
        }
        return ResourceProgressResponse.from(progress);
    }

    private Resource findPublished(UUID id) {
        return resourceRepository.findByIdAndStatus(id, ResourceStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
