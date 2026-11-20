package com.ecommerce.server.dto.brand;

import java.math.BigDecimal;

public record BrandResponse(
        Long id,
        String name,
        String countryOfMade,
        BigDecimal rating,
        String description
) {}
