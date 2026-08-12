package com.studen.showcase;

import java.util.UUID;

public record ProjectMediaResponse(
        UUID id, ProjectMediaType mediaType, String url, String thumbnailUrl, int displayOrder, boolean cover) {

    public static ProjectMediaResponse from(ProjectMedia media) {
        return new ProjectMediaResponse(
                media.getId(), media.getMediaType(), media.getUrl(), media.getThumbnailUrl(),
                media.getDisplayOrder(), media.isCover());
    }
}
