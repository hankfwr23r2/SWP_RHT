package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, Long> {

    @EntityGraph(attributePaths = {"staff", "staff.user", "staff.user.role"})
    List<CrewAssignment> findByVehicleTrip_IdInOrderById(Collection<Long> vehicleTripIds);

    @Query("""
            SELECT a.staff.userId FROM CrewAssignment a
            WHERE a.vehicleTrip.id = :tripId AND a.status <> :released AND a.crewRole IN :roles
            ORDER BY a.staff.userId
            """)
    List<Long> findAssignedStaffIds(Long tripId, CrewAssignmentStatus released, Collection<CrewRole> roles);

    @Query("""
            SELECT DISTINCT a.vehicleTrip.code FROM CrewAssignment a
            WHERE a.staff.userId IN :staffIds AND a.status <> :released AND a.crewRole IN :roles
                AND a.vehicleTrip.id <> :tripId AND a.vehicleTrip.status = :running
            ORDER BY a.vehicleTrip.code
            """)
    List<String> findRunningTripCodes(Collection<Long> staffIds, Long tripId, CrewAssignmentStatus released,
            Collection<CrewRole> roles, VehicleTripStatus running);

    // Phân công còn hiệu lực của một nhân viên trên các lượt chưa hủy: kiểm trùng giờ và nơi lượt trước kết thúc
    @EntityGraph(attributePaths = {"vehicleTrip", "vehicleTrip.startLocation", "vehicleTrip.endLocation"})
    @Query("""
            SELECT a FROM CrewAssignment a
            WHERE a.staff.userId = :staffId AND a.status <> :released AND a.vehicleTrip.status <> :cancelled
            """)
    List<CrewAssignment> findActiveOfStaff(@Param("staffId") Long staffId,
                                           @Param("released") CrewAssignmentStatus released,
                                           @Param("cancelled") VehicleTripStatus cancelled);

    @EntityGraph(attributePaths = {"staff", "vehicleTrip", "vehicleTrip.endLocation"})
    @Query("""
            SELECT a FROM CrewAssignment a
            WHERE a.staff.userId IN :staffIds AND a.status <> :released AND a.vehicleTrip.status <> :cancelled
            """)
    List<CrewAssignment> findActiveOfStaffIds(@Param("staffIds") Collection<Long> staffIds,
            @Param("released") CrewAssignmentStatus released, @Param("cancelled") VehicleTripStatus cancelled);

    // Lượt xe của tôi: phân công chưa bị gỡ, lượt chưa xong hoặc chưa hủy
    @EntityGraph(attributePaths = {"vehicleTrip", "vehicleTrip.vehicle", "vehicleTrip.startLocation", "vehicleTrip.endLocation"})
    @Query("""
            SELECT a FROM CrewAssignment a
            WHERE a.staff.user.username = :username AND a.status <> :released
              AND (a.vehicleTrip.status NOT IN :ended OR
                   (a.vehicleTrip.status = com.example.racehorse_transport.entity.enums.VehicleTripStatus.COMPLETED
                    AND EXISTS (SELECT 1 FROM Incident i WHERE i.vehicleTrip = a.vehicleTrip
                        AND i.status <> com.example.racehorse_transport.entity.enums.IncidentStatus.CLOSED)))
            ORDER BY CASE WHEN a.vehicleTrip.status = com.example.racehorse_transport.entity.enums.VehicleTripStatus.COMPLETED THEN 1 ELSE 0 END,
                     a.vehicleTrip.plannedStartAt
            """)
    List<CrewAssignment> findOpenOfUser(@Param("username") String username,
                                        @Param("released") CrewAssignmentStatus released,
                                        @Param("ended") Collection<VehicleTripStatus> ended);

    @EntityGraph(attributePaths = {"vehicleTrip", "vehicleTrip.vehicle", "vehicleTrip.startLocation", "vehicleTrip.endLocation", "staff", "staff.user"})
    @Query("SELECT a FROM CrewAssignment a WHERE a.id = :id")
    Optional<CrewAssignment> findWithTripAndStaff(@Param("id") Long id);

    boolean existsByVehicleTrip_IdAndStaff_UserIdAndStatusNot(Long vehicleTripId, Long staffId, CrewAssignmentStatus status);

    // Người đang trong tổ lái của lượt (chưa bị gỡ)
    @EntityGraph(attributePaths = {"staff", "staff.user"})
    Optional<CrewAssignment> findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(Long vehicleTripId, String username, CrewAssignmentStatus status);

    boolean existsByVehicleTrip_IdAndCrewRoleAndStatus(Long vehicleTripId, com.example.racehorse_transport.entity.enums.CrewRole crewRole, CrewAssignmentStatus status);
}
