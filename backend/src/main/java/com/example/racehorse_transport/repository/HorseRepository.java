package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Horse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Integer> {
    java.util.List<Horse> findByCustomerID_Id(Integer customerId);

    // Hồ sơ mới thêm hoặc vừa chỉnh sửa đứng trước
    @org.springframework.data.jpa.repository.Query("""
            select h from Horse h where h.customerID.id = :customerId
            order by coalesce(h.updatedAt, h.createdAt) desc nulls last, h.id desc""")
    java.util.List<Horse> findByCustomerNewestFirst(@org.springframework.data.repository.query.Param("customerId") Integer customerId);
    boolean existsByMicrochipIDIgnoreCase(String microchipID);
    boolean existsByMicrochipIDIgnoreCaseAndIdNot(String microchipID, Integer id);
    boolean existsByHorseTypeID_Id(Integer horseTypeId);
}
