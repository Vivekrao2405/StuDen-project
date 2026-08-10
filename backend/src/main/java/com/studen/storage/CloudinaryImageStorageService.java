package com.studen.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.studen.common.exception.StorageException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CloudinaryImageStorageService implements ImageStorageService {

    private final Cloudinary cloudinary;

    public CloudinaryImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public String upload(String publicId, MultipartFile file) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", publicId,
                    "overwrite", true,
                    "invalidate", true,
                    "resource_type", "image"));
            Object secureUrl = result.get("secure_url");
            if (secureUrl == null) {
                throw new StorageException("Image storage provider did not return a URL", null);
            }
            return secureUrl.toString();
        } catch (Exception e) {
            // Cloudinary's SDK throws both checked IOExceptions (network failures) and
            // unchecked exceptions (e.g. misconfigured credentials) — either way it's an
            // upstream storage failure, not an application bug, so both map to the same
            // 502 rather than leaking as a generic 500.
            throw new StorageException("Failed to upload image to storage provider", e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
        } catch (Exception e) {
            throw new StorageException("Failed to delete image from storage provider", e);
        }
    }
}
