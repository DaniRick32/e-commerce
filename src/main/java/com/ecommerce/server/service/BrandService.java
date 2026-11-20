package com.ecommerce.server.service;

import com.ecommerce.server.cache.ProductSearchCache;
import com.ecommerce.server.dto.brand.BrandRequest;
import com.ecommerce.server.dto.brand.BrandResponse;
import com.ecommerce.server.dto.brand.BrandUpdateRequest;
import com.ecommerce.server.entity.Brand;
import com.ecommerce.server.repository.BrandRepository;
import com.ecommerce.server.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BrandService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductSearchCache productSearchCache;

    public BrandService(
            BrandRepository brandRepository,
            ProductRepository productRepository,
            ProductSearchCache productSearchCache
    ) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.productSearchCache = productSearchCache;
    }

    public List<BrandResponse> findAll() {
        return brandRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BrandResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        String name = request.name().trim();

        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A brand with this name already exists"
            );
        }

        Brand brand = new Brand(
                name,
                request.countryOfMade(),
                request.rating(),
                request.description()
        );

        return toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse update(Long id, BrandUpdateRequest request) {
        Brand brand = findEntity(id);

        if (request.name() != null) {
            String name = request.name().trim();

            if (name.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Name must not be blank"
                );
            }

            if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A brand with this name already exists"
                );
            }

            brand.setName(name);
        }

        if (request.countryOfMade() != null) {
            brand.setCountryOfMade(request.countryOfMade());
        }

        if (request.rating() != null) {
            brand.setRating(request.rating());
        }

        if (request.description() != null) {
            brand.setDescription(request.description());
        }

        BrandResponse response = toResponse(brandRepository.save(brand));

        // Product responses embed brand details, so cached searches go stale.
        invalidateSearchCacheAfterCommit();

        return response;
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = findEntity(id);

        if (productRepository.existsByBrandId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Brand is still assigned to products"
            );
        }

        brandRepository.delete(brand);
    }

    private Brand findEntity(Long id) {
        return brandRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Brand not found"
                ));
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

    private BrandResponse toResponse(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getCountryOfMade(),
                brand.getRating(),
                brand.getDescription()
        );
    }
}
