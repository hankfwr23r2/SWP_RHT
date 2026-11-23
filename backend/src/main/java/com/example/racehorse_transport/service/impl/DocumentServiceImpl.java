package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.DocumentTypeRepository;
import com.example.racehorse_transport.service.DocumentService;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// Tải tệp lên Cloudinary và ghi document gắn với đúng một chủ sở hữu.
// Không kiểm người tải lên có quyền với chủ sở hữu hay không: service của từng tính năng kiểm trước khi gọi attach.
// Chủ sở hữu có 13 loại nên nạp bằng EntityManager theo class, thay vì inject 13 repository (AGENTS.md mục 7.1).
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final DocumentTypeRepository documentTypes;
    private final AppUserRepository users;
    private final CloudinaryClient storage;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Long attach(MultipartFile file, String documentTypeCode, DocumentSubject ownerSubject, Long ownerId, Long uploadedByUserId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp tải lên đang trống.");
        }
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "file" : file.getOriginalFilename();
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên tệp dài quá " + MAX_FILE_NAME_LENGTH + " ký tự.");
        }
        DocumentType type = documentTypes.findByCode(documentTypeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại giấy " + documentTypeCode));
        if (type.getOwnerType() != ownerSubject) {
            throw new IllegalArgumentException("Loại giấy " + documentTypeCode + " chỉ gắn được với " + type.getOwnerType() + ", không gắn với " + ownerSubject + ".");
        }
        AppUser uploader = users.findById(uploadedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + uploadedByUserId));

        Document document = new Document();
        document.setDocumentType(type);
        document.setFileName(fileName);
        document.setUploadedBy(uploader);
        document.setStatus(DocumentStatus.UPLOADED);
        setOwner(document, ownerSubject, ownerId);

        String url = storage.uploadFile(file, "documents/" + ownerSubject.name().toLowerCase(Locale.ROOT));
        deleteUploadOnRollback(url);
        document.setFileUrl(url);
        return documents.saveAndFlush(document).getId();
    }

    // Lỗi ghi DB sau khi đã tải lên thì xóa tệp, để Cloudinary không giữ tệp không thuộc document nào.
    private void deleteUploadOnRollback(String url) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.deleteFile(url);
                }
            }
        });
    }

    private void setOwner(Document document, DocumentSubject subject, Long ownerId) {
        switch (subject) {
            case HORSE -> document.setHorse(find(Horse.class, ownerId, subject));
            case BOOKING -> document.setBooking(find(Booking.class, ownerId, subject));
            case BOOKING_HORSE -> document.setBookingHorse(find(BookingHorse.class, ownerId, subject));
            case HORSE_LEG -> document.setHorseLeg(find(HorseLeg.class, ownerId, subject));
            case VEHICLE_TRIP -> document.setVehicleTrip(find(VehicleTrip.class, ownerId, subject));
            case INSURANCE -> document.setHorseInsurance(find(HorseInsurance.class, ownerId, subject));
            case INCIDENT -> document.setIncident(find(Incident.class, ownerId, subject));
            case COST_ITEM -> document.setCostItem(find(CostItem.class, ownerId, subject));
            case HANDOVER -> document.setHandover(find(Handover.class, ownerId, subject));
            case VEHICLE -> document.setVehicle(find(Vehicle.class, ownerId, subject));
            case STAFF -> document.setStaff(find(Staff.class, ownerId, subject));
            case PARTY -> document.setParty(find(Party.class, ownerId, subject));
            case CUSTOMER -> document.setCustomer(find(Customer.class, ownerId, subject));
        }
    }

    private <T> T find(Class<T> ownerClass, Long ownerId, DocumentSubject subject) {
        T owner = ownerId == null ? null : entityManager.find(ownerClass, ownerId);
        if (owner == null) {
            throw new ResourceNotFoundException("Không tìm thấy " + subject + " " + ownerId);
        }
        return owner;
    }
}
