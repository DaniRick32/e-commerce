package com.ecommerce.server.dto.brand;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BrandRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must contain at most 100 characters")
        String name,

        @Size(max = 100, message = "Country of made must contain at most 100 characters")
        String countryOfMade,

        @DecimalMin(value = "0.00", message = "Rating must be zero or greater")
        @DecimalMax(value = "5.00", message = "Rating must be five or less")
        @Digits(integer = 1, fraction = 2, message = "Rating must have at most 1 digit and 2 decimals")
        BigDecimal rating,

        String description
) {}
