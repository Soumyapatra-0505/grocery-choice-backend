package com.grocerychoice.backend.repository;

import com.grocerychoice.backend.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignationRepository extends JpaRepository<Designation, Long> {
    Optional<Designation> findByTitleIgnoreCase(String title);
    boolean existsByTitleIgnoreCase(String title);
    List<Designation> findAllByOrderByTitleAsc();
}
