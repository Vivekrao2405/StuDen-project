package com.studen.communication.audience;

import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single entry point every admin-communications consumer uses to turn a filter definition
 * into an audience — count, resolved user IDs, or a small non-technical preview sample. Never
 * called from a controller directly with raw JSON; controllers pass the stored/submitted
 * {@code filterJson} string straight through here.
 */
@Service
public class AudienceService {

    private static final int PREVIEW_SAMPLE_SIZE = 5;

    private final UserRepository userRepository;
    private final AudienceFilterParser parser;
    private final AudienceSpecificationBuilder specificationBuilder;

    public AudienceService(UserRepository userRepository, AudienceFilterParser parser,
            AudienceSpecificationBuilder specificationBuilder) {
        this.userRepository = userRepository;
        this.parser = parser;
        this.specificationBuilder = specificationBuilder;
    }

    @Transactional(readOnly = true)
    public long count(String filterJson) {
        return userRepository.count(toSpecification(filterJson));
    }

    @Transactional(readOnly = true)
    public List<UUID> resolve(String filterJson) {
        return userRepository.findAll(toSpecification(filterJson)).stream().map(User::getId).toList();
    }

    // First names only, small sample — the count/preview must always come from the backend and
    // never leak more than the minimal PII an admin needs to sanity-check the audience they built.
    @Transactional(readOnly = true)
    public List<String> previewSample(String filterJson) {
        return userRepository.findAll(toSpecification(filterJson), PageRequest.of(0, PREVIEW_SAMPLE_SIZE)).stream()
                .map(AudienceService::firstName)
                .toList();
    }

    private static String firstName(User user) {
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            return "Student";
        }
        int space = fullName.indexOf(' ');
        return space > 0 ? fullName.substring(0, space) : fullName;
    }

    private Specification<User> toSpecification(String filterJson) {
        AudienceFilterNode node = parser.parse(filterJson);
        return specificationBuilder.build(node);
    }
}
