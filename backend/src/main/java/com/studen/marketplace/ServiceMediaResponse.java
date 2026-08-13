package com.studen.marketplace;

import com.studen.showcase.ProjectMediaType;
import java.util.UUID;

public record ServiceMediaResponse(
        UUID id, ProjectMediaType mediaType, String url, String thumbnailUrl, int displayOrder, boolean cover) {

    public static ServiceMediaResponse from(ServiceMedia media) {
        return new ServiceMediaResponse(
                media.getId(), media.getMediaType(), media.getUrl(), media.getThumbnailUrl(),
                media.getDisplayOrder(), media.isCover());
    }
}
