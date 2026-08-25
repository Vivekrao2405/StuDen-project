package com.studen.resource;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Difficulty;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.storage.DocumentValidator;
import com.studen.storage.MediaStorageService;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin/content management for learning resources (Phase 7.7) — mirrors
 * {@code AdminPracticalAssessmentService}'s posture (assumes ADMIN already checked by
 * {@code @PreAuthorize} on the controller, constructor injection only) but with a deliberately
 * simpler DRAFT/PUBLISHED/ARCHIVED lifecycle: no REVIEW step, no versioning, and — unlike
 * Question/PracticalAssessment — a resource stays editable even once PUBLISHED, since it isn't a
 * scored/historical artifact whose fidelity needs freezing.
 */
@Service
public class AdminResourceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final MediaStorageService mediaStorageService;
    private final DocumentValidator documentValidator;

    public AdminResourceService(ResourceRepository resourceRepository, StudentResourceProgressRepository progressRepository,
            SkillRepository skillRepository, UserRepository userRepository, MediaStorageService mediaStorageService,
            DocumentValidator documentValidator) {
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.mediaStorageService = mediaStorageService;
        this.documentValidator = documentValidator;
    }

    @Transactional(readOnly = true)
    public ResourcePageResponse<ResourceSummaryResponse> list(UUID skillId, ResourceType resourceType,
            Difficulty difficulty, ResourceStatus status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<Resource> result = resourceRepository.search(skillId, resourceType, difficulty, status, normalizedSearch, pageable);
        return ResourcePageResponse.of(result.map(ResourceSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public ResourceDetailResponse get(UUID id) {
        return ResourceDetailResponse.from(findResource(id));
    }

    @Transactional
    public ResourceDetailResponse create(UUID userId, ResourceRequest request) {
        Skill skill = findSkill(request.skillId());
        User createdBy = findUser(userId);

        Resource resource = new Resource(request.title().trim(), request.resourceType(), skill, createdBy);
        applyRequest(resource, request, skill);
        return ResourceDetailResponse.from(resourceRepository.save(resource));
    }

    @Transactional
    public ResourceDetailResponse update(UUID id, ResourceRequest request) {
        Resource resource = findResource(id);
        Skill skill = findSkill(request.skillId());
        applyRequest(resource, request, skill);
        return ResourceDetailResponse.from(resource);
    }

    @Transactional
    public void delete(UUID id) {
        Resource resource = findResource(id);
        if (progressRepository.existsByResourceId(id)) {
            throw new ConflictException("Students have already started or completed this resource — archive it instead");
        }
        if (resource.getFilePublicId() != null) {
            deleteStoredFile(resource);
        }
        resourceRepository.delete(resource);
    }

    @Transactional
    public ResourceDetailResponse publish(UUID id) {
        Resource resource = findResource(id);
        if (resource.getStatus() == ResourceStatus.PUBLISHED) {
            throw new ConflictException("This resource is already published");
        }
        validateForPublish(resource);
        resource.setStatus(ResourceStatus.PUBLISHED);
        return ResourceDetailResponse.from(resource);
    }

    @Transactional
    public ResourceDetailResponse unpublish(UUID id) {
        Resource resource = findResource(id);
        if (resource.getStatus() != ResourceStatus.PUBLISHED) {
            throw new ConflictException("Only a published resource can be unpublished");
        }
        resource.setStatus(ResourceStatus.DRAFT);
        return ResourceDetailResponse.from(resource);
    }

    @Transactional
    public ResourceDetailResponse archive(UUID id) {
        Resource resource = findResource(id);
        if (resource.getStatus() == ResourceStatus.ARCHIVED) {
            throw new ConflictException("This resource is already archived");
        }
        resource.setStatus(ResourceStatus.ARCHIVED);
        return ResourceDetailResponse.from(resource);
    }

    @Transactional
    public ResourceDetailResponse uploadFile(UUID id, MultipartFile file) {
        Resource resource = findResource(id);
        if (resource.getResourceType() != ResourceType.PDF && resource.getResourceType() != ResourceType.DOCUMENT) {
            throw new ConflictException("Only PDF/DOCUMENT resources accept a file upload");
        }
        documentValidator.validate(file, resource.getResourceType());

        String publicId = filePublicId(resource.getId());
        String secureUrl = mediaStorageService.uploadDocument(publicId, file);
        resource.setFileUrl(secureUrl);
        resource.setFilePublicId(publicId);
        resource.setFileContentType(file.getContentType());
        resource.setFileName(file.getOriginalFilename());
        return ResourceDetailResponse.from(resource);
    }

    @Transactional
    public ResourceDetailResponse deleteFile(UUID id) {
        Resource resource = findResource(id);
        deleteStoredFile(resource);
        return ResourceDetailResponse.from(resource);
    }

    private void deleteStoredFile(Resource resource) {
        if (resource.getFilePublicId() != null) {
            mediaStorageService.deleteDocument(resource.getFilePublicId());
        }
        resource.setFileUrl(null);
        resource.setFilePublicId(null);
        resource.setFileContentType(null);
        resource.setFileName(null);
    }

    private void applyRequest(Resource resource, ResourceRequest request, Skill skill) {
        resource.setTitle(request.title().trim());
        resource.setDescription(blankToNull(request.description()));
        resource.setResourceType(request.resourceType());
        resource.setSkill(skill);
        resource.setDifficulty(request.difficulty());
        resource.setEstimatedMinutes(request.estimatedMinutes());
        resource.setExternalUrl(blankToNull(request.externalUrl()));
        resource.setNotesContent(blankToNull(request.notesContent()));
        Set<String> tags = request.tags() == null ? Set.of()
                : request.tags().stream().map(String::trim).filter(t -> !t.isEmpty())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        resource.setTags(tags);
    }

    // Spec §23: only a resource with real content for its type may be published.
    private void validateForPublish(Resource resource) {
        switch (resource.getResourceType()) {
            case PDF, DOCUMENT -> {
                if (resource.getFileUrl() == null) {
                    throw new InvalidRequestException("Upload a file before publishing this resource");
                }
            }
            case EXTERNAL_LINK, VIDEO -> {
                if (resource.getExternalUrl() == null) {
                    throw new InvalidRequestException("Add a URL before publishing this resource");
                }
            }
            case NOTES -> {
                if (resource.getNotesContent() == null) {
                    throw new InvalidRequestException("Add notes content before publishing this resource");
                }
            }
        }
    }

    private String filePublicId(UUID resourceId) {
        return "studen/resources/" + resourceId + "/file";
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Resource findResource(UUID id) {
        return resourceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
    }

    private Skill findSkill(UUID id) {
        return skillRepository.findById(id).orElseThrow(() -> new InvalidRequestException("Skill not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
