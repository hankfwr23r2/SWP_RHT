package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseDocumentRepository extends JpaRepository<HorseDocument, Integer> {

    /** Lấy tất cả hồ sơ của một con ngựa theo HorseID */
    List<HorseDocument> findByHorseID_Id(Integer horseId);

    /** Lấy hồ sơ của mọi con ngựa thuộc một khách hàng */
    List<HorseDocument> findByHorseID_CustomerID_User_UsernameIgnoreCase(String username);

    /** Lấy hồ sơ của một con ngựa theo loại giấy tờ */
    List<HorseDocument> findByHorseID_IdAndDocumentType(Integer horseId, String documentType);
}

