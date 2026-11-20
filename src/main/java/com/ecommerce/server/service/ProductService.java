package com.ecommerce.server.service;

import com.ecommerce.server.cache.ProductSearchCache;
import com.ecommerce.server.dto.product.ProductRequest;
import com.ecommerce.server.dto.product.ProductResponse;
import com.ecommerce.server.dto.product.ProductUpdateRequest;
import com.ecommerce.server.entity.Brand;
import com.ecommerce.server.entity.Category;
import com.ecommerce.server.entity.Product;
import com.ecommerce.server.repository.BrandRepository;
import com.ecommerce.server.repository.CategoryRepository;
import com.ecommerce.server.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductSearchCache productSearchCache;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            ProductSearchCache productSearchCache
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productSearchCache = productSearchCache;
    }

    public List<ProductResponse> findAll() {
        return productRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProductResponse> search(
            String q,
            Long categoryId,
            Long brandId,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minPrice must be zero or greater"
            );
        }

        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "maxPrice must be zero or greater"
            );
        }

        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minPrice must be less than or equal to maxPrice"
            );
        }

        String query = q == null || q.isBlank() ? null : q.trim();

        ProductSearchCache.CacheLookup cached =
                productSearchCache.lookup(query, categoryId, brandId, minPrice, maxPrice);

        if (cached.isHit()) {
            return cached.results();
        }

        List<ProductResponse> results = productRepository
                .search(query, categoryId, brandId, minPrice, maxPrice)
                .stream()
                .map(this::toResponse)
                .toList();

        productSearchCache.put(cached.key(), results);

        return results;
    }

    public List<ProductResponse> findByBrand(Long brandId) {
        if (!brandRepository.existsById(brandId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Brand not found"
            );
        }

        return productRepository
                .findByBrandIdOrderByNameAscIdAsc(brandId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String name = request.name().trim();

        if (productRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A product with this name already exists"
            );
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository
                    .findById(request.categoryId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Category not found"
                    ));
        }

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository
                    .findById(request.brandId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Brand not found"
                    ));
        }

        Product product = new Product(
                name,
                request.description(),
                request.price(),
                request.stock() == null ? 0 : request.stock(),
                category,
                brand
        );

        ProductResponse response = toResponse(productRepository.save(product));
        invalidateSearchCacheAfterCommit();
        return response;
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = productRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        if (request.name() != null) {
            String name = request.name().trim();

            if (name.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Name must not be blank"
                );
            }

            if (productRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A product with this name already exists"
                );
            }

            product.setName(name);
        }

        if (request.description() != null) {
            product.setDescription(request.description());
        }

        if (request.price() != null) {
            product.setPrice(request.price());
        }

        if (request.stock() != null) {
            product.setStock(request.stock());
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository
                    .findById(request.categoryId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Category not found"
                    ));

            product.setCategory(category);
        }

        if (request.brandId() != null) {
            Brand brand = brandRepository
                    .findById(request.brandId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Brand not found"
                    ));

            product.setBrand(brand);
        }

        ProductResponse response = toResponse(productRepository.save(product));
        invalidateSearchCacheAfterCommit();
        return response;
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        productRepository.delete(product);
        invalidateSearchCacheAfterCommit();
    }

    /**
     * Clears cached searches once the surrounding transaction has committed,
     * so a concurrent search cannot cache the pre-commit state under the new
     * cache generation.
     */
    private void invalidateSearchCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            productSearchCache.invalidate();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                productSearchCache.invalidate();
            }
        });
    }

    private ProductResponse toResponse(Product product) {
        Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
        String categoryName = product.getCategory() == null ? null : product.getCategory().getName();

        Brand brand = product.getBrand();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                categoryId,
                categoryName,
                brand == null ? null : brand.getId(),
                brand == null ? null : brand.getName(),
                brand == null ? null : brand.getCountryOfMade(),
                brand == null ? null : brand.getRating(),
                product.getCreatedAt()
        );
    }
}
