package com.studen.marketplace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceController {

    private final MarketplaceSearchService marketplaceSearchService;

    public MarketplaceController(MarketplaceSearchService marketplaceSearchService) {
        this.marketplaceSearchService = marketplaceSearchService;
    }

    @GetMapping
    public MarketplaceSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) String skill,
            // Omitted = both students and services ("All" tab) — preserves the pre-6.3 default
            // behavior exactly for any caller that doesn't pass this.
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer maxDeliveryDays,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return marketplaceSearchService.search(q, category, location, availability, skill, type, minPrice, maxPrice,
                maxDeliveryDays, sort, page, size);
    }
}
