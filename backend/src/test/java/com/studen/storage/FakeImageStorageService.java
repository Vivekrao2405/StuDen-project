package com.studen.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Replaces {@link CloudinaryImageStorageService} in every {@code @SpringBootTest} — picked up
 * automatically by component scanning since it shares the app's base package. Tests must never
 * depend on real Cloudinary credentials; this stands in for the provider with an in-memory fake
 * that still mimics its key behavior (a version bump on every overwrite, so "replace" produces a
 * genuinely different URL).
 */
@Component
@Primary
public class FakeImageStorageService implements ImageStorageService {

    private final AtomicLong version = new AtomicLong();
    private final Map<String, String> stored = new ConcurrentHashMap<>();

    @Override
    public String upload(String publicId, MultipartFile file) {
        String url = "https://res.cloudinary.com/fake-cloud/image/upload/v" + version.incrementAndGet()
                + "/" + publicId + ".jpg";
        stored.put(publicId, url);
        return url;
    }

    @Override
    public void delete(String publicId) {
        stored.remove(publicId);
    }

    public boolean isDeleted(String publicId) {
        return !stored.containsKey(publicId);
    }
}
