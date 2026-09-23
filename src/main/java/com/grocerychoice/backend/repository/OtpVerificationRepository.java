package com.grocerychoice.backend.repository;

import com.grocerychoice.backend.entity.OtpPurpose;
import com.grocerychoice.backend.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByIdentifierAndPurposeOrderByCreatedAtDesc(String identifier, OtpPurpose purpose);

    Optional<OtpVerification> findTopByIdentifierAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(String identifier, OtpPurpose purpose);

    List<OtpVerification> findByIdentifier(String identifier);

    void deleteByIdentifier(String identifier);
}
