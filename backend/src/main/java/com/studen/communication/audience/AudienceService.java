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

    // `marketing` must be the same value the caller will actually send with (a campaign's own
    // `isMarketing()`, or false for anything not tied to a real send — segment previews, for
    // instance, have no marketing concept of their own) — count/resolve/previewSample all compile
    // the filter through the identical AudienceSpecificationBuilder.build(node, marketing) path, so
    // there is exactly one place marketing-opt-out exclusion is implemented, never a second
    // parallel check that could drift from it.
    @Transactional(readOnly = true)
    public long count(String filterJson, boolean marketing) {
        return userRepository.count(toSpecification(filterJson, marketing));
    }

    @Transactional(readOnly = true)
    public List<UUID> resolve(String filterJson, boolean marketing) {
        return userRepository.findAll(toSpecification(filterJson, marketing)).stream().map(User::getId).toList();
    }

    // First names only, small sample — the count/preview must always come from the backend and
    // never leak more than the minimal PII an admin needs to sanity-check the audience they built.
    @Transactional(readOnly = true)
    public List<String> previewSample(String filterJson, boolean marketing) {
        return userRepository
                .findAll(toSpecification(filterJson, marketing), PageRequest.of(0, PREVIEW_SAMPLE_SIZE)).stream()
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

    private Specification<User> toSpecification(String filterJson, boolean marketing) {
        AudienceFilterNode node = parser.parse(filterJson);
        return specificationBuilder.build(node, marketing);
    }
}
