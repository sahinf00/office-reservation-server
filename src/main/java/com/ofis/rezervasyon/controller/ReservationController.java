package com.ofis.rezervasyon.controller;

import java.time.LocalDate;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofis.rezervasyon.dto.request.CreateReservationRequest;
import com.ofis.rezervasyon.dto.response.ReservationResponse;
import com.ofis.rezervasyon.enums.ReservationStatus;
import com.ofis.rezervasyon.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@RequestBody @Valid CreateReservationRequest request) {
        ReservationResponse response = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReservationResponse>> getAllReservationsForAdmin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reservationDate,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false)  ReservationStatus status,
            @PageableDefault(size = 10, sort = "reservationDate, id", direction = Sort.Direction.DESC) 
            @ParameterObject Pageable pageable
            
    ) {
        Page<ReservationResponse> response = reservationService.getAllReservationsForAdmin(reservationDate, floorId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-reservations")
    public ResponseEntity<List<ReservationResponse>> getReservationsForCurrentUser() {
        List<ReservationResponse> response = reservationService.getReservationsForCurrentUser();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reserved-desks")
    public ResponseEntity<List<Long>> getReservedDeskIdsForDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reservationDate) {
        List<Long> reservedDeskIds = reservationService.getReservedDeskIdsForDate(reservationDate);
        return ResponseEntity.ok(reservedDeskIds);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.noContent().build();
    }
}
