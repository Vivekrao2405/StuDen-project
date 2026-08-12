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
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return marketplaceSearchService.search(q, category, location, availability, skill, sort, page, size);
    }
}
