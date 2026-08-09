package com.studen.portfolio;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
class SlugGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final StudentPortfolioRepository portfolioRepository;

    SlugGenerator(StudentPortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    String generateUniqueSlug(String fullName) {
        String base = slugify(fullName);
        String candidate = base;

        while (portfolioRepository.existsByPublicSlug(candidate)) {
            candidate = base + "-" + randomSuffix();
        }

        return candidate;
    }

    private String slugify(String value) {
        String slug = value.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-|-$", "");

        return slug.isBlank() ? "student" : slug;
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(SUFFIX_CHARS.charAt(RANDOM.nextInt(SUFFIX_CHARS.length())));
        }
        return suffix.toString();
    }
}
