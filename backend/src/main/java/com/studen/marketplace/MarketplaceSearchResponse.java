package com.studen.marketplace;

import java.util.List;

public record MarketplaceSearchResponse(
        List<MarketplaceResultResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static MarketplaceSearchResponse of(List<MarketplaceResultResponse> sorted, int page, int size) {
        long totalElements = sorted.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        // long arithmetic guards against a huge `page` overflowing int before the clamp below.
        int fromIndex = (int) Math.min((long) page * size, sorted.size());
        int toIndex = (int) Math.min((long) fromIndex + size, sorted.size());
        List<MarketplaceResultResponse> content = sorted.subList(fromIndex, toIndex);

        return new MarketplaceSearchResponse(content, page, size, totalElements, totalPages);
    }
}
