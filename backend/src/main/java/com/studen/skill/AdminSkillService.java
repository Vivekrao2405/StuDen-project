package com.studen.skill;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of the shared skill catalog — the Placement system's Skill Management screen,
 * and reused by any other admin surface that needs to curate skills directly (students get the
 * existing search/create-custom-skill flow in {@link SkillController} unchanged).
 *
 * <p>The catalog has no description or enable/disable status field (unlike {@code PlacementRole}
 * and {@code PlacementCompany}), so this service does not invent either — only name and category
 * are editable, matching what {@link Skill} actually carries.
 */
@Service
public class AdminSkillService {

    private static final int MAX_PAGE_SIZE = 100;

    // A skill created directly by an admin has no known brand logo either — same fallback
    // SkillService uses for a student-typed custom skill.
    private static final String DEFAULT_ICON = "Sparkles";

    private final SkillRepository skillRepository;

    public AdminSkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public SkillPageResponse<AdminSkillResponse> list(String search, String category, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.ASC, "name"));
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();
        Page<Skill> result = skillRepository.searchAdmin(normalizedSearch, normalizedCategory, pageable);
        return SkillPageResponse.of(result.map(AdminSkillResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminSkillResponse get(UUID id) {
        return AdminSkillResponse.from(findSkill(id));
    }

    @Transactional
    public AdminSkillResponse create(CreateSkillRequest request) {
        String name = request.name().trim();
        String normalized = normalize(name);
        if (skillRepository.findByNormalizedName(normalized).isPresent()) {
            throw new ConflictException("A skill with this name already exists");
        }
        Skill skill = new Skill(name, normalized, request.category().trim(), DEFAULT_ICON, IconType.LUCIDE);
        return AdminSkillResponse.from(skillRepository.save(skill));
    }

    @Transactional
    public AdminSkillResponse update(UUID id, CreateSkillRequest request) {
        Skill skill = findSkill(id);
        String name = request.name().trim();
        String normalized = normalize(name);
        skillRepository.findByNormalizedName(normalized).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("A skill with this name already exists");
            }
        });
        skill.setName(name);
        skill.setNormalizedName(normalized);
        skill.setCategory(request.category().trim());
        return AdminSkillResponse.from(skill);
    }

    /**
     * Only allowed while nothing points at this skill. Several of the catalog's consuming tables
     * (a student's portfolio skills, marketplace service tags, showcase project tags) cascade on
     * skill delete rather than restrict it, so checking first is the only way to avoid silently
     * deleting a student's own data along with the catalog row.
     */
    @Transactional
    public void delete(UUID id) {
        Skill skill = findSkill(id);
        if (skillRepository.isInUse(id)) {
            throw new ConflictException(
                    "This skill is in use (questions, assessments, resources, role mappings, portfolios, "
                            + "or other data) and cannot be deleted");
        }
        skillRepository.delete(skill);
    }

    private Skill findSkill(UUID id) {
        return skillRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
