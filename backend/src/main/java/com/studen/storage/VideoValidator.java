package com.studen.storage;

import com.studen.common.exception.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side video validation, mirroring {@link ImageValidator}: the claimed content-type from
 * the browser is never trusted on its own — the first bytes of the file are sniffed to confirm
 * it's actually one of the supported formats before it's ever handed to the storage provider.
 */
@Component
public class VideoValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("video/mp4", "video/webm");

    private final long maxSizeBytes;

    public VideoValidator(@Value("${app.video.max-size-mb}") long maxSizeMb) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A video file is required");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidRequestException("Video must be " + (maxSizeBytes / (1024 * 1024)) + "MB or smaller");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidRequestException("Only MP4 and WEBM videos are supported");
        }

        byte[] header = readHeader(file);
        if (!matchesDeclaredType(header, contentType.toLowerCase())) {
            throw new InvalidRequestException("The file does not appear to be a valid video");
        }
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < header.length) {
                throw new InvalidRequestException("The file does not appear to be a valid video");
            }
        } catch (IOException e) {
            throw new InvalidRequestException("Unable to read the uploaded file");
        }
        return header;
    }

    private boolean matchesDeclaredType(byte[] h, String contentType) {
        return switch (contentType) {
            // MP4/ISO-BMFF: the first 4 bytes are a box-size integer that varies per file, but
            // bytes 4-7 are always the literal ASCII box type "ftyp" for a valid MP4.
            case "video/mp4" -> h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
            // WebM is a Matroska container; every Matroska/WebM file starts with the EBML header.
            case "video/webm" -> (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                    && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
            default -> false;
        };
    }
}
