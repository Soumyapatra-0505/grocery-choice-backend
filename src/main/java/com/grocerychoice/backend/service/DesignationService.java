package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.DesignationRequest;
import com.grocerychoice.backend.entity.AuditLog;
import com.grocerychoice.backend.entity.Designation;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.AuditLogRepository;
import com.grocerychoice.backend.repository.DesignationRepository;
import com.grocerychoice.backend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DesignationService {

    private static final Logger log = LoggerFactory.getLogger(DesignationService.class);

    private final DesignationRepository designationRepository;
    private final AuditLogRepository auditLogRepository;

    private static final List<String[]> DEFAULT_DESIGNATIONS = List.of(
            new String[]{"Store Owner", "Full business and operational authority for Grocery Choice"},
            new String[]{"Store Manager", "Overall store operations, staff supervision, and daily management"},
            new String[]{"Inventory Manager", "Responsible for inventory, stock audits, and replenishment"},
            new String[]{"Sales Manager", "Leads sales promotions, pricing strategy, and customer satisfaction"},
            new String[]{"Operations Manager", "Oversees daily workflows, logistics, and store readiness"},
            new String[]{"Accountant", "Manages financial records, cash flow, invoicing, and expenses"},
            new String[]{"Customer Support", "Resolves customer inquiries, feedback, and return requests"},
            new String[]{"Delivery Manager", "Coordinates order packing, dispatching, and delivery riders"},
            new String[]{"Warehouse Manager", "Supervises storage, inward shipments, and order fulfillment"}
    );

    public DesignationService(DesignationRepository designationRepository, AuditLogRepository auditLogRepository) {
        this.designationRepository = designationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public List<Designation> getAllDesignations() {
        if (designationRepository.count() == 0) {
            log.info("No designations found. Seeding default business designations...");
            for (String[] def : DEFAULT_DESIGNATIONS) {
                Designation d = new Designation(def[0], def[1]);
                designationRepository.save(d);
            }
        }
        return designationRepository.findAllByOrderByTitleAsc();
    }

    @Transactional
    public Designation createDesignation(DesignationRequest request, UserPrincipal actor) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new InvalidDataException("Designation title is required");
        }
        String title = request.getTitle().trim();
        if (designationRepository.existsByTitleIgnoreCase(title)) {
            throw new InvalidDataException("Designation already exists: " + title);
        }

        Designation designation = new Designation(title, request.getDescription() != null ? request.getDescription().trim() : null);
        Designation saved = designationRepository.save(designation);

        auditLogRepository.save(new AuditLog(
                "DESIGNATION_CREATED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                saved.getId(),
                null,
                saved.getTitle(),
                "Created designation: " + saved.getTitle()
        ));

        log.info("Created new designation: {} by actor: {}", saved.getTitle(), actor != null ? actor.getEmail() : "system");
        return saved;
    }

    @Transactional
    public Designation updateDesignation(Long id, DesignationRequest request, UserPrincipal actor) {
        Designation existing = designationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found with ID: " + id));

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            String newTitle = request.getTitle().trim();
            if (!newTitle.equalsIgnoreCase(existing.getTitle()) && designationRepository.existsByTitleIgnoreCase(newTitle)) {
                throw new InvalidDataException("Designation title already in use: " + newTitle);
            }
            existing.setTitle(newTitle);
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription().trim());
        }

        Designation updated = designationRepository.save(existing);

        auditLogRepository.save(new AuditLog(
                "DESIGNATION_UPDATED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                updated.getId(),
                null,
                updated.getTitle(),
                "Updated designation: " + updated.getTitle()
        ));

        return updated;
    }

    @Transactional
    public void deleteDesignation(Long id, UserPrincipal actor) {
        Designation existing = designationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found with ID: " + id));

        designationRepository.delete(existing);

        auditLogRepository.save(new AuditLog(
                "DESIGNATION_DELETED",
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : "system",
                actor != null ? actor.getFullName() : "System",
                id,
                null,
                existing.getTitle(),
                "Deleted designation: " + existing.getTitle()
        ));

        log.info("Deleted designation: {} by actor: {}", existing.getTitle(), actor != null ? actor.getEmail() : "system");
    }
}
