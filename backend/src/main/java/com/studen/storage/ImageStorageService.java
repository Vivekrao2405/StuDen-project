package com.studen.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads/deletes images against a stable, caller-chosen public ID (rather than a
 * provider-generated one) so replacing an image is an atomic overwrite, not an
 * upload-then-orphan-cleanup two-step.
 */
public interface ImageStorageService {

    String upload(String publicId, MultipartFile file);

    void delete(String publicId);
}
