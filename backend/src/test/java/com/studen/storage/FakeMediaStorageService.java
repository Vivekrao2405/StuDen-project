package com.studen.storage;

import com.studen.common.exception.StorageException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Replaces {@link CloudinaryMediaStorageService} in every {@code @SpringBootTest} — picked up
 * automatically by component scanning since it shares the app's base package. Tests must never
 * depend on real Cloudinary credentials; this stands in for the provider with an in-memory fake
 * that still mimics its key behavior (a version bump on every overwrite, so "replace" produces a
 * genuinely different URL).
 */
@Component
@Primary
public class FakeMediaStorageService implements MediaStorageService {

    private final AtomicLong version = new AtomicLong();
    private final Map<String, String> stored = new ConcurrentHashMap<>();
    private final Map<String, String> storedVideos = new ConcurrentHashMap<>();
    private final Map<String, String> storedDocuments = new ConcurrentHashMap<>();
    private final Map<String, byte[]> documentBytesByUrl = new ConcurrentHashMap<>();

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

    @Override
    public VideoUploadResult uploadVideo(String publicId, MultipartFile file) {
        long v = version.incrementAndGet();
        String url = "https://res.cloudinary.com/fake-cloud/video/upload/v" + v + "/" + publicId + ".mp4";
        String thumbnailUrl = "https://res.cloudinary.com/fake-cloud/video/upload/v" + v + "/" + publicId + ".jpg";
        storedVideos.put(publicId, url);
        return new VideoUploadResult(url, thumbnailUrl);
    }

    @Override
    public void deleteVideo(String publicId) {
        storedVideos.remove(publicId);
    }

    @Override
    public String uploadDocument(String publicId, MultipartFile file) {
        String url = "https://res.cloudinary.com/fake-cloud/raw/upload/v" + version.incrementAndGet()
                + "/" + publicId;
        storedDocuments.put(publicId, url);
        try {
            documentBytesByUrl.put(url, file.getBytes());
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded document bytes", e);
        }
        return url;
    }

    @Override
    public void deleteDocument(String publicId) {
        storedDocuments.remove(publicId);
    }

    @Override
    public byte[] downloadDocument(String secureUrl) {
        byte[] bytes = documentBytesByUrl.get(secureUrl);
        if (bytes == null) {
            throw new StorageException("Document storage provider returned status 404", null);
        }
        return bytes;
    }

    public boolean isDeleted(String publicId) {
        return !stored.containsKey(publicId);
    }

    public boolean isVideoDeleted(String publicId) {
        return !storedVideos.containsKey(publicId);
    }

    public boolean isDocumentDeleted(String publicId) {
        return !storedDocuments.containsKey(publicId);
    }
}
