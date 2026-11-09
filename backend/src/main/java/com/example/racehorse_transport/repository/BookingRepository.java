package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer> {
    @org.springframework.data.jpa.repository.Query("select b.id from Booking b where b.status in :statuses and b.departureDate < :today order by b.id")
    List<Integer> findScheduleChangeCandidates(
            @org.springframework.data.repository.query.Param("statuses") Collection<String> statuses,
            @org.springframework.data.repository.query.Param("today") java.time.Instant today);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Booking b where b.id = :id")
    java.util.Optional<Booking> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    // Các danh sách đều đọc tên và tài khoản khách của từng đơn: nạp kèm trong cùng câu truy vấn để không tốn 2 câu SQL mỗi đơn
    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    List<Booking> findByCustomerID_IdOrderByBookingDateDesc(Integer customerId);
    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    Page<Booking> findByCustomerID_IdOrderByBookingDateDesc(Integer customerId, Pageable pageable);

    List<Booking> findByStatusOrderByBookingDateAsc(String status);

    // Chỉ lấy ID để không nạp trạng thái cũ vào persistence context trước khi khóa từng đơn.
    @org.springframework.data.jpa.repository.Query("select b.id from Booking b where b.status = :status order by b.bookingDate asc, b.id asc")
    List<Integer> findIdsByStatusOrderByBookingDateAsc(@org.springframework.data.repository.query.Param("status") String status);
    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    List<Booking> findByStatusOrderByBookingDateDesc(String status);
    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    Page<Booking> findByStatusOrderByBookingDateDesc(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    List<Booking> findByStatusInOrderByBookingDateDesc(Collection<String> statuses);
    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    List<Booking> findAllByOrderByBookingDateDesc();
    @EntityGraph(attributePaths = {"customerID", "customerID.user"})
    Page<Booking> findAllByOrderByBookingDateDesc(Pageable pageable);

    java.util.Optional<Booking> findByWaybillNoIgnoreCase(String waybillNo);
}
