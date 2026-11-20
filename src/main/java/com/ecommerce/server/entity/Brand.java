package com.ecommerce.server.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "brands")
public class Brand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "country_of_made", length = 100)
    private String countryOfMade;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Brand() {}

    public Brand(String name, String countryOfMade, BigDecimal rating, String description) {
        this.name = name;
        this.countryOfMade = countryOfMade;
        this.rating = rating;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCountryOfMade() { return countryOfMade; }
    public BigDecimal getRating() { return rating; }
    public String getDescription() { return description; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCountryOfMade(String countryOfMade) { this.countryOfMade = countryOfMade; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public void setDescription(String description) { this.description = description; }
}
