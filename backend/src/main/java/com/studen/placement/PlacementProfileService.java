package com.studen.placement;

import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student placement profile: read and upsert, scoped to the caller own user id — there is no
 * endpoint that lets one student read or write another profile.
 *
 * <p>Upsert rather than create-then-update on purpose: the spec requires that a returning student
 * is not made to repeat onboarding, and the unique constraint on user_id means a second profile
 * could never be created anyway. Saving again simply updates the existing row.
 *
 * <p>Phase 0 stops here. Readiness scoring, skill gaps and recommendations read this profile in
 * later phases; none of that is computed in this service.
 */
@Service
public class PlacementProfileService {

    private final PlacementProfileRepository profileRepository;
    private final PlacementRoleRepository roleRepository;
    private final PlacementCompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public PlacementProfileService(PlacementProfileRepository profileRepository,
            PlacementRoleRepository roleRepository, PlacementCompanyRepository companyRepository,
            SkillRepository skillRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns the caller profile, or empty when they have not onboarded yet. Empty is a normal
     * state, not an error, so the caller gets 204 rather than 404.
     */
    @Transactional(readOnly = true)
    public Optional<PlacementProfileResponse> findMyProfile(UUID userId) {
        return profileRepository.findByUserId(userId).map(PlacementProfileResponse::from);
    }

    @Transactional
    public PlacementProfileResponse save(UUID userId, PlacementProfileRequest request) {
        PlacementRole targetRole = roleRepository.findById(request.targetRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Target role not found"));
        if (targetRole.getStatus() != PlacementCatalogStatus.ACTIVE) {
            throw new InvalidRequestException("This role is not currently available as a placement target");
        }

        PlacementProfile profile = profileRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return new PlacementProfile(user, targetRole, request.experienceLevel());
        });

        profile.setTargetRole(targetRole);
        profile.setExperienceLevel(request.experienceLevel());

        profile.getCompanyTypes().clear();
        if (request.companyTypes() != null) {
            profile.getCompanyTypes().addAll(request.companyTypes());
        }

        profile.getTargetCompanies().clear();
        profile.getTargetCompanies().addAll(loadCompanies(request.targetCompanyIds()));

        profile.getCurrentSkills().clear();
        profile.getCurrentSkills().addAll(loadSkills(request.currentSkillIds()));

        return PlacementProfileResponse.from(profileRepository.save(profile));
    }

    @Transactional
    public void deleteMyProfile(UUID userId) {
        PlacementProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Placement profile not found"));
        // Cascades only to the profile own link tables; companies, skills and the role survive.
        profileRepository.delete(profile);
    }

    private Set<PlacementCompany> loadCompanies(List<UUID> companyIds) {
        Set<PlacementCompany> result = new LinkedHashSet<>();
        if (companyIds == null || companyIds.isEmpty()) {
            return result;
        }
        Map<UUID, PlacementCompany> found = new HashMap<>();
        for (PlacementCompany company : companyRepository.findAllById(new LinkedHashSet<>(companyIds))) {
            found.put(company.getId(), company);
        }
        for (UUID id : companyIds) {
            PlacementCompany company = found.get(id);
            if (company == null) {
                throw new ResourceNotFoundException("Company not found: " + id);
            }
            result.add(company);
        }
        return result;
    }

    private Set<Skill> loadSkills(List<UUID> skillIds) {
        Set<Skill> result = new LinkedHashSet<>();
        if (skillIds == null || skillIds.isEmpty()) {
            return result;
        }
        Map<UUID, Skill> found = new HashMap<>();
        for (Skill skill : skillRepository.findAllByIdIn(new LinkedHashSet<>(skillIds))) {
            found.put(skill.getId(), skill);
        }
        for (UUID id : skillIds) {
            Skill skill = found.get(id);
            if (skill == null) {
                throw new ResourceNotFoundException("Skill not found: " + id);
            }
            result.add(skill);
        }
        return result;
    }
}
