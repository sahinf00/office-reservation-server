package com.ofis.rezervasyon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofis.rezervasyon.dto.request.CreateDeskRequest;
import com.ofis.rezervasyon.dto.request.UpdateDeskRequest;
import com.ofis.rezervasyon.dto.response.DeskResponse;
import com.ofis.rezervasyon.model.Desk;
import com.ofis.rezervasyon.model.Floor;
import com.ofis.rezervasyon.repository.DeskRepository;
import com.ofis.rezervasyon.repository.FloorRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeskService {
    private final DeskRepository deskRepository;
    private final FloorRepository floorRepository;
    private final ReservationService reservationService; // for providing deleteDesk method reservation cancellation

    @Transactional
    public DeskResponse createDesk(CreateDeskRequest request) {
        Floor floor = floorRepository.findById(request.floorId())
            .orElseThrow(() -> new EntityNotFoundException("Floor with ID " + request.floorId() + " does not exist."));
        
        if (deskRepository.existsByDeskNumberAndFloorIdAndIsActiveTrue(request.deskNumber(), request.floorId())) {
            throw new IllegalStateException("Desk with number " + request.deskNumber() + " already exists.");
        }

        Desk desk = new Desk();
        desk.setDeskNumber(request.deskNumber());
        desk.setFloor(floor);
        desk.setActive(true);

        Desk savedDesk = deskRepository.save(desk);
        return new DeskResponse(
            savedDesk.getId(),
            savedDesk.getDeskNumber(),
            savedDesk.isActive(),
            savedDesk.getFloor().getId()
        );
    }

    @Transactional
    public DeskResponse updateDesk(Long id, UpdateDeskRequest request) {
        Desk desk = deskRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Desk with ID " + id + " does not exist."));

        if (!desk.isActive()) {
        throw new IllegalStateException("Inactive desks cannot be updated.");
        }

        // Does this request change the desk number?
        boolean isDeskNumberChanged = !desk.getDeskNumber().equalsIgnoreCase(request.deskNumber());

        // If the desk number is changed, check if the new number is already in use by another active desk
        if (isDeskNumberChanged) {
            boolean isNumberTaken = deskRepository.existsByDeskNumberAndFloorIdAndIsActiveTrue(
                request.deskNumber(), 
                desk.getFloor().getId()
            );

        if (isNumberTaken) {
            throw new IllegalStateException("This floor already has a desk with number " + request.deskNumber() + ".");
        }
    }
        desk.setDeskNumber(request.deskNumber());
        Desk updatedDesk = deskRepository.save(desk);
        return new DeskResponse(
            updatedDesk.getId(),
            updatedDesk.getDeskNumber(),
            updatedDesk.isActive(),
            updatedDesk.getFloor().getId()
        );
    }

    @Transactional
    public void deleteDesk(Long id) {
        Desk desk = deskRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Desk not found: " + id));

        if (!desk.isActive()) {
        throw new IllegalStateException("This desk is already inactive/deleted.");
        }
    
        desk.setActive(false);
        deskRepository.save(desk);
        reservationService.cancelFutureReservationsForDesk(id);
    }
}