package com.studen.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads/deletes images and videos against a stable, caller-chosen public ID (rather than a
 * provider-generated one) so replacing a media item is an atomic overwrite, not an
 * upload-then-orphan-cleanup two-step.
 */
public interface MediaStorageService {

    String upload(String publicId, MultipartFile file);

    void delete(String publicId);

    VideoUploadResult uploadVideo(String publicId, MultipartFile file);

    void deleteVideo(String publicId);
}
