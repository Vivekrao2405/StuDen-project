package com.studen.showcase;

import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.storage.ImageValidator;
import com.studen.storage.MediaStorageService;
import com.studen.storage.VideoUploadResult;
import com.studen.storage.VideoValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final StudentPortfolioRepository portfolioRepository;
    private final SkillRepository skillRepository;
    private final ImageValidator imageValidator;
    private final VideoValidator videoValidator;
    private final MediaStorageService mediaStorageService;
    private final int maxProjectsPerStudent;
    private final int maxImagesPerProject;
    private final int maxVideosPerProject;

    public ProjectService(ProjectRepository projectRepository, ProjectMediaRepository projectMediaRepository,
            StudentPortfolioRepository portfolioRepository, SkillRepository skillRepository,
            ImageValidator imageValidator, VideoValidator videoValidator, MediaStorageService mediaStorageService,
            @Value("${app.project.max-projects-per-student}") int maxProjectsPerStudent,
            @Value("${app.project.max-images-per-project}") int maxImagesPerProject,
            @Value("${app.project.max-videos-per-project}") int maxVideosPerProject) {
        this.projectRepository = projectRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.portfolioRepository = portfolioRepository;
        this.skillRepository = skillRepository;
        this.imageValidator = imageValidator;
        this.videoValidator = videoValidator;
        this.mediaStorageService = mediaStorageService;
        this.maxProjectsPerStudent = maxProjectsPerStudent;
        this.maxImagesPerProject = maxImagesPerProject;
        this.maxVideosPerProject = maxVideosPerProject;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMyProjects(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        return projectRepository.findAllByPortfolioIdOrderByCreatedAtDesc(portfolio.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse createProject(UUID userId, ProjectRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);

        if (projectRepository.countByPortfolioId(portfolio.getId()) >= maxProjectsPerStudent) {
            throw new InvalidRequestException(
                    "You've reached the maximum number of projects (" + maxProjectsPerStudent + ")");
        }

        Project project = new Project(portfolio, request.title());
        applyRequest(project, request);
        project = projectRepository.save(project);

        return toResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID userId, UUID projectId, ProjectRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Project project = findOwnProject(portfolio.getId(), projectId);
        applyRequest(project, request);
        return toResponse(project);
    }

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Project project = findOwnProject(portfolio.getId(), projectId);

        project.getMedia().forEach(this::deleteFromStorage);
        projectRepository.delete(project);
    }

    /** Called by {@code PortfolioService} when the whole portfolio is deleted, so this student's
     * project media doesn't leak on Cloudinary after the DB rows cascade-delete. Not exposed via
     * any controller — {@code portfolioId} is already verified-owned by the caller. */
    @Transactional
    public void deleteAllForPortfolio(UUID portfolioId) {
        List<Project> projects = projectRepository.findAllByPortfolioIdOrderByCreatedAtDesc(portfolioId);
        projects.forEach(project -> project.getMedia().forEach(this::deleteFromStorage));
        projectRepository.deleteAll(projects);
    }

    @Transactional
    public ProjectResponse uploadMedia(UUID userId, UUID projectId, MultipartFile file) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Project project = findOwnProject(portfolio.getId(), projectId);

        ProjectMediaType mediaType = detectMediaType(file);
        enforceMediaLimit(project.getId(), mediaType);

        String publicId = mediaPublicId(portfolio.getId(), project.getId());
        int nextOrder = projectMediaRepository.findAllByProjectIdOrderByDisplayOrderAsc(project.getId()).size();

        ProjectMedia media;
        if (mediaType == ProjectMediaType.IMAGE) {
            imageValidator.validate(file);
            String url = mediaStorageService.upload(publicId, file);
            media = new ProjectMedia(project, ProjectMediaType.IMAGE, url, publicId, nextOrder);
        } else {
            videoValidator.validate(file);
            VideoUploadResult result = mediaStorageService.uploadVideo(publicId, file);
            media = new ProjectMedia(project, ProjectMediaType.VIDEO, result.secureUrl(), publicId, nextOrder);
            media.setThumbnailUrl(result.thumbnailUrl());
        }

        // The first media item on a project automatically becomes the cover, so a fresh project
        // never sits on the placeholder once at least one item exists — still changeable later.
        if (nextOrder == 0) {
            media.setCover(true);
        }
        projectMediaRepository.save(media);

        return toResponse(project);
    }

    @Transactional
    public ProjectResponse removeMedia(UUID userId, UUID projectId, UUID mediaId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Project project = findOwnProject(portfolio.getId(), projectId);
        ProjectMedia media = findOwnMedia(project.getId(), mediaId);

        deleteFromStorage(media);
        projectMediaRepository.delete(media);

        return toResponse(project);
    }

    @Transactional
    public ProjectResponse reorderMedia(UUID userId, UUID projectId, UpdateMediaOrderRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Project project = findOwnProject(portfolio.getId(), projectId);

        List<ProjectMedia> existing = projectMediaRepository.findAllByProjectIdOrderByDisplayOrderAsc(project.getId());
        Set<UUID> existingIds = existing.stream().map(ProjectMedia::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(request.mediaIds());

        if (!existingIds.equals(requestedIds)) {
            throw new InvalidRequestException("The media order must include exactly this project's own media items");
        }

        Map<UUID, ProjectMedia> byId = existing.stream()
                .collect(Collectors.toMap(ProjectMedia::getId, m -> m));
        List<UUID> order = request.mediaIds();
        for (int i = 0; i < order.size(); i++) {
            byId.get(order.get(i)).setDisplayOrder(i);
        }

        return toResponse(project);
    }

    @Transactional
    public ProjectResponse setCoverMedia(UUID userId, UUID projectId, UUID mediaId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Project project = findOwnProject(portfolio.getId(), projectId);
        findOwnMedia(project.getId(), mediaId);

        projectMediaRepository.findAllByProjectIdOrderByDisplayOrderAsc(project.getId())
                .forEach(m -> m.setCover(m.getId().equals(mediaId)));

        return toResponse(project);
    }

    private void enforceMediaLimit(UUID projectId, ProjectMediaType mediaType) {
        long count = projectMediaRepository.countByProjectIdAndMediaType(projectId, mediaType);
        int max = mediaType == ProjectMediaType.IMAGE ? maxImagesPerProject : maxVideosPerProject;
        if (count >= max) {
            String label = mediaType == ProjectMediaType.IMAGE ? "images" : "videos";
            throw new InvalidRequestException("You've reached the maximum number of " + label + " (" + max + ") for this project");
        }
    }

    private ProjectMediaType detectMediaType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A media file is required");
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return ProjectMediaType.IMAGE;
        }
        if (contentType != null && contentType.toLowerCase().startsWith("video/")) {
            return ProjectMediaType.VIDEO;
        }
        throw new InvalidRequestException("Unsupported file type — only images and videos are allowed");
    }

    private void deleteFromStorage(ProjectMedia media) {
        if (media.getMediaType() == ProjectMediaType.VIDEO) {
            mediaStorageService.deleteVideo(media.getPublicId());
        } else {
            mediaStorageService.delete(media.getPublicId());
        }
    }

    private String mediaPublicId(UUID portfolioId, UUID projectId) {
        return "studen/portfolios/" + portfolioId + "/projects/" + projectId + "/media/" + UUID.randomUUID();
    }

    private void applyRequest(Project project, ProjectRequest request) {
        project.setTitle(request.title().trim());
        project.setShortDescription(blankToNull(request.shortDescription()));
        project.setDescription(blankToNull(request.description()));
        project.setVisibility(request.visibility() == null ? ProjectVisibility.PUBLIC : request.visibility());

        Set<UUID> skillIds = request.skillIds() == null ? Set.of() : request.skillIds();
        List<Skill> resolvedSkills = skillRepository.findAllByIdIn(skillIds);
        project.setSkills(new LinkedHashSet<>(resolvedSkills));

        List<ProjectLink> links = request.links() == null
                ? new ArrayList<>()
                : request.links().stream()
                        .map(l -> new ProjectLink(l.label().trim(), l.url().trim()))
                        .collect(Collectors.toCollection(ArrayList::new));
        project.setLinks(links);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ProjectResponse toResponse(Project project) {
        List<ProjectMedia> media = projectMediaRepository.findAllByProjectIdOrderByDisplayOrderAsc(project.getId());
        return ProjectResponse.from(project, media, CoverMediaResolver.resolve(media));
    }

    private StudentPortfolio findOwnPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));
    }

    private Project findOwnProject(UUID portfolioId, UUID projectId) {
        return projectRepository.findByIdAndPortfolioId(projectId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private ProjectMedia findOwnMedia(UUID projectId, UUID mediaId) {
        return projectMediaRepository.findByIdAndProjectId(mediaId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project media not found"));
    }
}
