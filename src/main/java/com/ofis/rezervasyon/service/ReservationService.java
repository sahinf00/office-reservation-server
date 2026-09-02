package com.ofis.rezervasyon.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofis.rezervasyon.dto.request.CreateReservationRequest;
import com.ofis.rezervasyon.dto.response.ReservationResponse;
import com.ofis.rezervasyon.enums.ReservationStatus;
import com.ofis.rezervasyon.model.Desk;
import com.ofis.rezervasyon.model.Reservation;
import com.ofis.rezervasyon.model.User;
import com.ofis.rezervasyon.repository.DeskRepository;
import com.ofis.rezervasyon.repository.ReservationRepository;
import com.ofis.rezervasyon.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {
    
    private final ReservationRepository reservationRepository;
    private final DeskRepository deskRepository;
    private final UserRepository userRepository;

    // helper method for mapping Reservation entity to ReservationResponse DTO
    private ReservationResponse mapToReservationResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getDesk().getId(),
            reservation.getDesk().getDeskNumber(),
            reservation.getDesk().getFloor().getFloorNumber(),
            reservation.getUser().getId(),
            reservation.getUser().getFullName(),
            reservation.getReservationDate(),
            reservation.getStatus()
        );
    }

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        Desk desk = deskRepository.findByIdWithLock(request.deskId())
            .orElseThrow(() -> new EntityNotFoundException("Desk with ID " + request.deskId() + " does not exist."));

        // checks for desk activity status before creating a reservation
        if (!desk.isActive()) {
            throw new IllegalStateException("Cannot create a reservation for an inactive desk.");
        }

        // retrieves the current authenticated user's email from the security context
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentUserEmail)
            .orElseThrow(() -> new EntityNotFoundException("User with email " + currentUserEmail + " does not exist."));

        // checks if the desk is already reserved for the selected date to prevent conflicts
        if (reservationRepository.existsByDeskIdAndReservationDateAndStatus(
            desk.getId(), 
            request.reservationDate(), 
            ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException("This desk is already reserved for the selected date.");
        }

        // checks if the user already has a reservation for the same date to prevent double booking
        if (reservationRepository.existsByUserIdAndReservationDateAndStatus(
            currentUser.getId(), 
            request.reservationDate(), 
            ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException("You already have an active reservation for a desk on the selected date.");
        }

        Reservation reservation = new Reservation();
        reservation.setDesk(desk);
        reservation.setUser(currentUser);
        reservation.setReservationDate(request.reservationDate());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        Reservation savedReservation = reservationRepository.save(reservation);

        return mapToReservationResponse(savedReservation);
    }

    @Transactional(readOnly = true)
    public List <ReservationResponse> getReservationsForCurrentUser() {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentUserEmail)
            .orElseThrow(() -> new EntityNotFoundException("User with email " + currentUserEmail + " does not exist."));

        List<Reservation> reservations = reservationRepository.findByUserIdWithDeskAndFloor(currentUser.getId());
        return reservations.stream()
                .map(this::mapToReservationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservationsForAdmin(LocalDate reservationDate, Long floorId, ReservationStatus status, Pageable pageable) {
        Page<Reservation> reservationsPage = reservationRepository.findAllWithFilters(reservationDate, floorId, status, pageable);
        return reservationsPage.map(this::mapToReservationResponse);
    }

    @Transactional(readOnly = true)
    public List<Long> getReservedDeskIdsForDate(LocalDate reservationDate) {
        List<Reservation> reservations = reservationRepository.findAllWithFilters(reservationDate, null, ReservationStatus.CONFIRMED, Pageable.unpaged()).getContent();
        return reservations.stream()
                .map(reservation -> reservation.getDesk().getId())
                .toList();
    }

    @Transactional
    public void cancelReservation(Long reservationId) {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentUserEmail)
            .orElseThrow(() -> new EntityNotFoundException("User with email " + currentUserEmail + " does not exist."));
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new EntityNotFoundException("Reservation with ID " + reservationId + " does not exist."));

        // checks if the reservation belongs to the current user 
        if (!reservation.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("You can only cancel your own reservations.");
        }

        // checks if the reservation is already canceled to prevent redundant operations
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("This reservation is already canceled.");
        }

        //check for date of reservation being in the past or today
        if (!reservation.getReservationDate().isAfter(java.time.LocalDate.now())) {
            throw new IllegalStateException("Cannot cancel a reservation made for today or a past date.");
        }

        // updates the reservation status to CANCELLED and saves the change
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }

    @Transactional
    public int updateCompletedReservations() {
        return reservationRepository.updateCompletedReservations(
            ReservationStatus.COMPLETED, 
            ReservationStatus.CONFIRMED, 
            LocalDate.now()
        );
    }
}
