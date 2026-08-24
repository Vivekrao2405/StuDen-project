package com.studen.storage;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studen.common.exception.InvalidRequestException;
import com.studen.resource.ResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

// Plain unit test — DocumentValidator has no Spring-context dependencies beyond a constructor
// arg, mirrors how a validator-only class would be tested (no MockMvc/@SpringBootTest needed).
class DocumentValidatorTest {

    private static final byte[] REAL_PDF_HEADER = "%PDF-1.4\n%rest of a real pdf".getBytes();
    private static final byte[] REAL_DOCX_HEADER = new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0};

    private DocumentValidator validator() {
        return new DocumentValidator(10);
    }

    @Test
    void validate_realPdfForPdfType_passes() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", REAL_PDF_HEADER);
        assertThatNoException().isThrownBy(() -> validator().validate(file, ResourceType.PDF));
    }

    @Test
    void validate_realDocxForDocumentType_passes() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", REAL_DOCX_HEADER);
        assertThatNoException().isThrownBy(() -> validator().validate(file, ResourceType.DOCUMENT));
    }

    @Test
    void validate_docxDeclaredAsPdfType_rejected() {
        // Content-type/bytes are a real DOCX, but the resource is typed PDF — PDF only accepts
        // application/pdf, regardless of what DOCUMENT would allow.
        MockMultipartFile file = new MockMultipartFile("file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", REAL_DOCX_HEADER);
        assertThatThrownBy(() -> validator().validate(file, ResourceType.PDF)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void validate_contentTypeClaimsPdfButBytesAreNot_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not actually a pdf".getBytes());
        assertThatThrownBy(() -> validator().validate(file, ResourceType.PDF)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void validate_unsupportedContentType_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", REAL_PDF_HEADER);
        assertThatThrownBy(() -> validator().validate(file, ResourceType.PDF)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void validate_oversizeFile_rejected() {
        DocumentValidator smallLimit = new DocumentValidator(0);
        MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", REAL_PDF_HEADER);
        assertThatThrownBy(() -> smallLimit.validate(file, ResourceType.PDF)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void validate_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> validator().validate(file, ResourceType.PDF)).isInstanceOf(InvalidRequestException.class);
    }
}
