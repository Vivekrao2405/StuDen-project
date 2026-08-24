package com.studen.storage;

import com.studen.common.exception.InvalidRequestException;
import com.studen.resource.ResourceType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side document validation, mirroring {@link ImageValidator}/{@link VideoValidator}: the
 * claimed content-type is never trusted alone — the first bytes are sniffed to confirm the file
 * actually is what it claims to be. PDF for {@link ResourceType#PDF}; PDF or DOCX for
 * {@link ResourceType#DOCUMENT} (legacy binary .doc is intentionally not sniffed/accepted — its
 * OLE2 container format isn't worth the extra complexity for this phase).
 */
@Component
public class DocumentValidator {

    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf");
    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of("application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final long maxSizeBytes;

    public DocumentValidator(@Value("${app.document.max-size-mb}") long maxSizeMb) {
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    public void validate(MultipartFile file, ResourceType resourceType) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A file is required");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidRequestException("The file must be " + (maxSizeBytes / (1024 * 1024)) + "MB or smaller");
        }

        Set<String> allowed = resourceType == ResourceType.PDF ? PDF_CONTENT_TYPES : DOCUMENT_CONTENT_TYPES;
        String contentType = file.getContentType();
        if (contentType == null || !allowed.contains(contentType.toLowerCase())) {
            throw new InvalidRequestException(resourceType == ResourceType.PDF ? "Only PDF files are supported"
                    : "Only PDF and DOCX files are supported");
        }

        byte[] header = readHeader(file);
        if (!matchesDeclaredType(header, contentType.toLowerCase())) {
            throw new InvalidRequestException("The file does not appear to be a valid " + contentType);
        }
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < 4) {
                throw new InvalidRequestException("The file does not appear to be valid");
            }
        } catch (IOException e) {
            throw new InvalidRequestException("Unable to read the uploaded file");
        }
        return header;
    }

    private boolean matchesDeclaredType(byte[] h, String contentType) {
        return switch (contentType) {
            // "%PDF-"
            case "application/pdf" -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-';
            // DOCX is a ZIP container — local file header signature "PK\x03\x04".
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    (h[0] & 0xFF) == 0x50 && (h[1] & 0xFF) == 0x4B && (h[2] & 0xFF) == 0x03 && (h[3] & 0xFF) == 0x04;
            default -> false;
        };
    }
}
