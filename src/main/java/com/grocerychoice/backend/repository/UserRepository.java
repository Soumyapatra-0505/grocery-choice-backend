package com.grocerychoice.backend.repository;

import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    List<User> findByRole(Role role);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:identifier) OR u.phone = :identifier OR REPLACE(REPLACE(REPLACE(u.phone, ' ', ''), '-', ''), '+91', '') = :cleanPhone")
    Optional<User> findByIdentifier(@org.springframework.data.repository.query.Param("identifier") String identifier, @org.springframework.data.repository.query.Param("cleanPhone") String cleanPhone);
}
