package com.studen.marketplace;

import com.studen.showcase.ProjectMediaType;
import java.util.Comparator;
import java.util.List;

/** Resolves a service's cover image URL — same rule as showcase's {@code CoverMediaResolver},
 * applied to {@link ServiceMedia}: explicit cover, else first image, else first video's
 * thumbnail, else null. Public so {@code com.studen.share.ShareService} can reuse it when
 * building the public service detail response. */
public final class ServiceCoverMediaResolver {

    private ServiceCoverMediaResolver() {
    }

    public static String resolve(List<ServiceMedia> media) {
        return media.stream()
                .filter(ServiceMedia::isCover)
                .map(ServiceMedia::getUrl)
                .findFirst()
                .or(() -> media.stream()
                        .filter(m -> m.getMediaType() == ProjectMediaType.IMAGE)
                        .min(Comparator.comparingInt(ServiceMedia::getDisplayOrder))
                        .map(ServiceMedia::getUrl))
                .or(() -> media.stream()
                        .filter(m -> m.getMediaType() == ProjectMediaType.VIDEO)
                        .min(Comparator.comparingInt(ServiceMedia::getDisplayOrder))
                        .map(ServiceMedia::getThumbnailUrl))
                .orElse(null);
    }
}
