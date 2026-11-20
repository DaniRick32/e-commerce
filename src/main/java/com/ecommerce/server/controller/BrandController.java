package com.ecommerce.server.controller;

import com.ecommerce.server.dto.brand.BrandRequest;
import com.ecommerce.server.dto.brand.BrandResponse;
import com.ecommerce.server.dto.brand.BrandUpdateRequest;
import com.ecommerce.server.dto.product.ProductResponse;
import com.ecommerce.server.service.BrandService;
import com.ecommerce.server.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
public class BrandController {
    private final BrandService brandService;
    private final ProductService productService;

    public BrandController(BrandService brandService, ProductService productService) {
        this.brandService = brandService;
        this.productService = productService;
    }

    @GetMapping
    public List<BrandResponse> getBrands() {
        return brandService.findAll();
    }

    @GetMapping("/{id}")
    public BrandResponse getBrand(@PathVariable Long id) {
        return brandService.findById(id);
    }

    @GetMapping("/{id}/products")
    public List<ProductResponse> getBrandProducts(@PathVariable Long id) {
        return productService.findByBrand(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse createBrand(@Valid @RequestBody BrandRequest request) {
        return brandService.create(request);
    }

    @PatchMapping("/{id}")
    public BrandResponse updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandUpdateRequest request
    ) {
        return brandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(@PathVariable Long id) {
        brandService.delete(id);
    }
}
