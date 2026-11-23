package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    @Query("SELECT n FROM Notification n WHERE " +
           "(:role IS NULL OR n.recipientRole IS NULL OR LOWER(n.recipientRole) = LOWER(:role)) AND " +
           "(:username IS NULL OR (LOWER(COALESCE(n.recipientRole, '')) != 'customer' AND n.recipientUsername IS NULL) OR LOWER(n.recipientUsername) = LOWER(:username)) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findForUser(@Param("role") String role, @Param("username") String username);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE " +
           "(:role IS NULL OR n.recipientRole IS NULL OR LOWER(n.recipientRole) = LOWER(:role)) AND " +
           "(:username IS NULL OR (LOWER(COALESCE(n.recipientRole, '')) != 'customer' AND n.recipientUsername IS NULL) OR LOWER(n.recipientUsername) = LOWER(:username))")
    int markAllAsRead(@Param("role") String role, @Param("username") String username);

    @Modifying
    @Query("DELETE FROM Notification n WHERE " +
           "(:role IS NULL OR n.recipientRole IS NULL OR LOWER(n.recipientRole) = LOWER(:role)) AND " +
           "(:username IS NULL OR (LOWER(COALESCE(n.recipientRole, '')) != 'customer' AND n.recipientUsername IS NULL) OR LOWER(n.recipientUsername) = LOWER(:username))")
    int deleteForUser(@Param("role") String role, @Param("username") String username);
}
