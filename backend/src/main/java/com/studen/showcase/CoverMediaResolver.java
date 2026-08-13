package com.studen.showcase;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves a project's cover image URL: the media item explicitly marked as cover, else the
 * first image by display order, else the first video's thumbnail, else null (a project with no
 * media at all — the frontend shows a default placeholder rather than the backend fabricating
 * one, per the "don't use the StuDen logo as a cover unless explicitly chosen" rule).
 *
 * Public so the marketplace module can reuse it when resolving a linked showcase project's cover
 * for a service response, rather than duplicating this logic.
 */
public final class CoverMediaResolver {

    private CoverMediaResolver() {
    }

    public static String resolve(List<ProjectMedia> media) {
        return media.stream()
                .filter(ProjectMedia::isCover)
                .map(ProjectMedia::getUrl)
                .findFirst()
                .or(() -> media.stream()
                        .filter(m -> m.getMediaType() == ProjectMediaType.IMAGE)
                        .min(Comparator.comparingInt(ProjectMedia::getDisplayOrder))
                        .map(ProjectMedia::getUrl))
                .or(() -> media.stream()
                        .filter(m -> m.getMediaType() == ProjectMediaType.VIDEO)
                        .min(Comparator.comparingInt(ProjectMedia::getDisplayOrder))
                        .map(ProjectMedia::getThumbnailUrl))
                .orElse(null);
    }
}
