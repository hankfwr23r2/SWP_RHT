package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// Gắn tệp vào chủ sở hữu trên DB thật. Cloudinary là dịch vụ ngoài nên được giả lập.
// Không chạy trong transaction của test, để kiểm được việc xóa tệp khi transaction của attach rollback.
@PostgresIntegrationTest
class DocumentServiceTest {

    @Autowired private DocumentService documentService;
    @Autowired private DocumentRepository documents;
    @Autowired private HorseRepository horses;
    @Autowired private StaffRepository staff;
    @Autowired private AppUserRepository users;
    @Autowired private TransactionTemplate transaction;
    @MockitoBean private CloudinaryClient storage;

    private final String urlPrefix = "https://files.test/" + UUID.randomUUID() + "/";
    private Long customerUserId;
    private Long horseId;

    @BeforeEach
    void setUp() {
        customerUserId = users.findByUsername("customer1.dev").orElseThrow().getId();
        horseId = horses.findAll().stream().filter(h -> h.getMicrochip().equals("985000000100001"))
                .map(Horse::getId).findFirst().orElseThrow();
    }

    @AfterEach
    void removeCreatedDocuments() {
        documents.deleteAll(documents.findAll().stream().filter(d -> d.getFileUrl().startsWith(urlPrefix)).toList());
    }

    @Test
    void attachesFileToTheOwnerOfItsDocumentType() {
        when(storage.uploadFile(any(), eq("documents/horse"))).thenReturn(urlPrefix + "passport.pdf");

        Long id = documentService.attach(pdf("passport.pdf"), "HORSE_PASSPORT", DocumentSubject.HORSE, horseId, customerUserId);

        transaction.executeWithoutResult(status -> {
            Document saved = documents.findById(id).orElseThrow();
            assertThat(saved.getHorse().getId()).isEqualTo(horseId);
            assertThat(saved.getBooking()).isNull();
            assertThat(saved.getDocumentType().getCode()).isEqualTo("HORSE_PASSPORT");
            assertThat(saved.getFileUrl()).isEqualTo(urlPrefix + "passport.pdf");
            assertThat(saved.getFileName()).isEqualTo("passport.pdf");
            assertThat(saved.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
            assertThat(saved.getUploadedBy().getId()).isEqualTo(customerUserId);
            assertThat(saved.getUploadedAt()).isNotNull();
        });
    }

    @Test
    void staffOwnerIsFoundByItsAccountKey() {
        Staff driver = staff.findByStaffCode("DRV-001").orElseThrow();
        when(storage.uploadFile(any(), eq("documents/staff"))).thenReturn(urlPrefix + "license.jpg");

        Long id = documentService.attach(pdf("license.jpg"), "DRIVER_LICENSE", DocumentSubject.STAFF, driver.getUserId(), driver.getUserId());

        transaction.executeWithoutResult(status ->
                assertThat(documents.findById(id).orElseThrow().getStaff().getStaffCode()).isEqualTo("DRV-001"));
    }

    @Test
    void documentTypeOfAnotherOwnerIsRejectedBeforeUploading() {
        assertThatThrownBy(() -> documentService.attach(pdf("record.pdf"), "HANDOVER_RECORD", DocumentSubject.HORSE, horseId, customerUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HANDOVER");

        verify(storage, never()).uploadFile(any(), anyString());
    }

    @Test
    void missingOwnerTypeOrUploaderIsRejectedBeforeUploading() {
        assertThatThrownBy(() -> documentService.attach(pdf("a.pdf"), "HORSE_PASSPORT", DocumentSubject.HORSE, -1L, customerUserId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> documentService.attach(pdf("a.pdf"), "NO_SUCH_TYPE", DocumentSubject.HORSE, horseId, customerUserId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> documentService.attach(pdf("a.pdf"), "HORSE_PASSPORT", DocumentSubject.HORSE, horseId, -1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storage, never()).uploadFile(any(), anyString());
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.attach(empty, "HORSE_PASSPORT", DocumentSubject.HORSE, horseId, customerUserId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).uploadFile(any(), anyString());
    }

    @Test
    void reviewUploadRejectsFileWithMisleadingExtensionAndContentTypeBeforeUpload() {
        MockMultipartFile spoofed = new MockMultipartFile("file", "passport.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> documentService.attachForReview(spoofed, "HEALTH_CERT", DocumentSubject.BOOKING, 1L,
                customerUserId, null, java.time.LocalDate.now()))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("PDF, JPG hoặc PNG");
        verify(storage, never()).uploadFile(any(), anyString());
    }

    @Test
    void uploadedFileIsDeletedWhenSavingFails() {
        String taken = urlPrefix + "taken.pdf";
        when(storage.uploadFile(any(), anyString())).thenReturn(taken);
        documentService.attach(pdf("first.pdf"), "HORSE_PASSPORT", DocumentSubject.HORSE, horseId, customerUserId);

        // file_url có UK: lần ghi thứ hai lỗi sau khi tệp đã được tải lên
        assertThatThrownBy(() -> documentService.attach(pdf("second.pdf"), "HORSE_PASSPORT", DocumentSubject.HORSE, horseId, customerUserId))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(storage).deleteFile(taken);
        assertThat(documents.findAll()).filteredOn(d -> d.getFileUrl().equals(taken)).hasSize(1);
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4".getBytes());
    }
}
