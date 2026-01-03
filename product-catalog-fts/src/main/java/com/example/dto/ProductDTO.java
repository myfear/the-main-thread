package com.example.dto;

public record ProductDTO(
        Long id,
        String name,
        String description,
        String category,
        Double price,
        Integer viewCount,
        String createdAt,
        String imageUrl,
        String cursor) {
}