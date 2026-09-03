package com.ofis.rezervasyon.repository;

import com.ofis.rezervasyon.enums.ReservationStatus;
import com.ofis.rezervasyon.model.Reservation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // prevents N+1 query problem during reservation history retrieval, by forcing eager fetching of associated desk and floor entities
    @EntityGraph(attributePaths = {"desk", "desk.floor"})
    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId ORDER BY r.reservationDate DESC")
    List<Reservation> findByUserIdWithDeskAndFloor(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :newStatus " +
           "WHERE r.status = :currentStatus AND r.reservationDate < :currentDate")
    int updateCompletedReservations(@Param("newStatus") ReservationStatus newStatus,
                                       @Param("currentStatus") ReservationStatus currentStatus,
                                       @Param("currentDate") LocalDate currentDate);

    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'CANCELLED' " +
            "WHERE r.desk.id= :deskId " +
            "AND r.reservationDate >= CURRENT_DATE " +
            "AND r.status = 'CONFIRMED'")
    void cancelFutureReservationsForDesk(@Param("deskId") Long deskId);

    @EntityGraph(attributePaths = {"desk", "desk.floor"})
    @Query("SELECT r FROM Reservation r " +
        "WHERE (cast(:reservationDate as date) IS NULL OR r.reservationDate = :reservationDate) " +
        "AND (cast(:floorId as long) IS NULL OR r.desk.floor.id = :floorId) " +
        "AND (cast(:status as string) IS NULL OR r.status = :status)")
    Page<Reservation> findAllWithFilters(
            @Param("reservationDate") LocalDate reservationDate,
            @Param("floorId") Long floorId,
            @Param("status") ReservationStatus status,
            Pageable pageable
    );

    long countByReservationDateAndStatus(LocalDate reservationDate, ReservationStatus status);
    boolean existsByUserIdAndReservationDateAndStatus(Long userId, LocalDate reservationDate, ReservationStatus status);

    boolean existsByDeskIdAndReservationDateAndStatus(Long deskId, LocalDate reservationDate, ReservationStatus status);
}
