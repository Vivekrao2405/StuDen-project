package com.studen.marketplace;

import com.studen.showcase.CoverMediaResolver;
import com.studen.showcase.Project;
import com.studen.showcase.ProjectMedia;
import java.util.List;
import java.util.UUID;

/** A small summary of a showcase project linked to a service — resolved at read time from
 * {@link Project}/{@link ProjectMedia}, never a copy, so the project stays the single source of
 * truth for its own title/media. */
public record ServiceProjectSummaryResponse(UUID id, String title, String coverImageUrl) {

    public static ServiceProjectSummaryResponse from(Project project, List<ProjectMedia> media) {
        return new ServiceProjectSummaryResponse(project.getId(), project.getTitle(), CoverMediaResolver.resolve(media));
    }
}
