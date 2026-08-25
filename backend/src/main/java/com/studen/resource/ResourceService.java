package com.studen.resource;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.storage.MediaStorageService;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Student-facing reads/actions. myLearning() is a thin pass-through to ResourceMatchingService —
// kept as a separate service (rather than folding matching logic in here) so the matching/
// aggregation algorithm stays independently testable.
@Service
public class ResourceService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9 ._-]");

    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final ResourceMatchingService matchingService;
    private final MediaStorageService mediaStorageService;

    public ResourceService(ResourceRepository resourceRepository, StudentResourceProgressRepository progressRepository,
            UserRepository userRepository, ResourceMatchingService matchingService, MediaStorageService mediaStorageService) {
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
        this.mediaStorageService = mediaStorageService;
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

    // Re-serves the uploaded PDF/DOCUMENT bytes with headers the app controls, rather than pointing
    // the browser at the raw Cloudinary secure_url directly: Cloudinary's "raw" resource type is
    // stored under an extension-less public_id (see AdminResourceService#filePublicId) so its own
    // delivery would otherwise guess application/octet-stream and force a download instead of
    // rendering the PDF inline (spec: My Learning "Continue" must open the PDF, not download it).
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> file(UUID resourceId, boolean download) {
        Resource resource = findPublished(resourceId);
        if (resource.getFileUrl() == null) {
            throw new ResourceNotFoundException("This resource has no file");
        }
        byte[] bytes = mediaStorageService.downloadDocument(resource.getFileUrl());
        String contentType = resource.getFileContentType() != null ? resource.getFileContentType() : "application/pdf";
        String disposition = (download ? "attachment" : "inline") + "; filename=\"" + buildFilename(resource) + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(bytes);
    }

    private String buildFilename(Resource resource) {
        String extension = DOCX_CONTENT_TYPE.equals(resource.getFileContentType()) ? ".docx" : ".pdf";
        String base = resource.getFileName() != null ? stripExtension(resource.getFileName()) : resource.getTitle();
        String safeBase = UNSAFE_FILENAME_CHARS.matcher(base == null ? "" : base).replaceAll("_").trim();
        return (safeBase.isEmpty() ? "resource" : safeBase) + extension;
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private Resource findPublished(UUID id) {
        return resourceRepository.findByIdAndStatus(id, ResourceStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
