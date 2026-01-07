package com.example.entity;

public class Product {
    public Long id;
    public String name;
    public String category;
    public Double price;
    public String sku;

    public Product() {
    }

    public Product(Long id, String name, String category, Double price, String sku) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.sku = sku;
    }
}