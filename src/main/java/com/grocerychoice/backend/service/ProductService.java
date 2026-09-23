package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.ProductRequest;
import com.grocerychoice.backend.dto.ProductResponse;
import com.grocerychoice.backend.entity.Category;
import com.grocerychoice.backend.entity.Product;
import com.grocerychoice.backend.exception.DuplicateResourceException;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.exception.ResourceNotFoundException;
import com.grocerychoice.backend.repository.CategoryRepository;
import com.grocerychoice.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts() {
        return productRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponse.fromEntity(product);
    }

    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        // Price validation: sellingPrice must not be greater than MRP
        if (request.getSellingPrice().compareTo(request.getMrp()) > 0) {
            throw new InvalidDataException("Selling price (" + request.getSellingPrice() + ") cannot be greater than MRP (" + request.getMrp() + ")");
        }

        // Stock validation: stockQuantity cannot be negative
        if (request.getStockQuantity() != null && request.getStockQuantity() < 0) {
            throw new InvalidDataException("Stock quantity cannot be negative");
        }

        // SKU uniqueness validation
        if (request.getSku() != null && !request.getSku().isBlank()) {
            String trimmedSku = request.getSku().trim();
            if (productRepository.existsBySkuIgnoreCase(trimmedSku)) {
                throw new DuplicateResourceException("Product with SKU '" + trimmedSku + "' already exists");
            }
        }

        Product product = new Product(
                request.getName().trim(),
                request.getDescription(),
                request.getSku() != null && !request.getSku().isBlank() ? request.getSku().trim() : null,
                category,
                request.getImageUrl(),
                request.getUnit(),
                request.getMrp(),
                request.getSellingPrice(),
                request.getStockQuantity() != null ? request.getStockQuantity() : 0,
                request.getActive() != null ? request.getActive() : true
        );

        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        // Price validation
        if (request.getSellingPrice().compareTo(request.getMrp()) > 0) {
            throw new InvalidDataException("Selling price (" + request.getSellingPrice() + ") cannot be greater than MRP (" + request.getMrp() + ")");
        }

        // Stock validation
        if (request.getStockQuantity() != null && request.getStockQuantity() < 0) {
            throw new InvalidDataException("Stock quantity cannot be negative");
        }

        // SKU uniqueness validation
        if (request.getSku() != null && !request.getSku().isBlank()) {
            String trimmedSku = request.getSku().trim();
            if (productRepository.existsBySkuIgnoreCaseAndIdNot(trimmedSku, id)) {
                throw new DuplicateResourceException("Product with SKU '" + trimmedSku + "' already exists");
            }
            product.setSku(trimmedSku);
        } else {
            product.setSku(null);
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setImageUrl(request.getImageUrl());
        product.setUnit(request.getUnit());
        product.setMrp(request.getMrp());
        product.setSellingPrice(request.getSellingPrice());
        product.setStockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0);
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        Product updated = productRepository.save(product);
        return ProductResponse.fromEntity(updated);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Soft delete: set active = false
        product.setActive(false);
        productRepository.save(product);
    }

    public ProductResponse updateStock(Long id, Integer stockQuantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (stockQuantity == null || stockQuantity < 0) {
            throw new InvalidDataException("Stock quantity cannot be negative");
        }

        product.setStockQuantity(stockQuantity);
        Product updated = productRepository.save(product);
        return ProductResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        return productRepository.findByCategoryIdAndActiveTrue(categoryId)
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return getActiveProducts();
        }

        return productRepository.searchActiveProducts(query.trim())
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
