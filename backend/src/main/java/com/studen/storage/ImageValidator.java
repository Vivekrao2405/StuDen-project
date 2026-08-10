package com.studen.storage;

import com.studen.common.exception.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side image validation: the claimed content-type/filename from the browser is never
 * trusted on its own — the first bytes of the file are sniffed to confirm it's actually one of
 * the supported formats before it's ever handed to the storage provider.
 */
@Component
public class ImageValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final long maxSizeBytes;

    public ImageValidator(@Value("${app.image.max-size-mb}") long maxSizeMb) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("An image file is required");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidRequestException("Image must be " + (maxSizeBytes / (1024 * 1024)) + "MB or smaller");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidRequestException("Only JPEG, PNG, and WEBP images are supported");
        }

        byte[] header = readHeader(file);
        if (!matchesDeclaredType(header, contentType.toLowerCase())) {
            throw new InvalidRequestException("The file does not appear to be a valid image");
        }
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < header.length) {
                throw new InvalidRequestException("The file does not appear to be a valid image");
            }
        } catch (IOException e) {
            throw new InvalidRequestException("Unable to read the uploaded file");
        }
        return header;
    }

    private boolean matchesDeclaredType(byte[] h, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "image/png" -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
            case "image/webp" -> h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }
}
