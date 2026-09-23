package com.grocerychoice.backend.repository;

import com.grocerychoice.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrueOrderByNameAsc();
    Optional<Product> findByIdAndActiveTrue(Long id);
    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);
    boolean existsByCategoryId(Long categoryId);
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Query("SELECT p FROM Product p WHERE p.active = true AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchActiveProducts(@Param("query") String query);
}
