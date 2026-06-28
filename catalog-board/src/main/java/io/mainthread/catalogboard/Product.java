package io.mainthread.catalogboard;

import java.util.List;
import java.util.Optional;

import org.hibernate.annotations.processing.Find;
import org.hibernate.annotations.processing.HQL;

import io.quarkus.hibernate.panache.PanacheEntity;
import io.quarkus.hibernate.panache.PanacheRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = "sku"))
public class Product extends PanacheEntity {

    @Column(nullable = false, length = 32)
    public String sku;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(nullable = false, length = 80)
    public String category;

    @Column(nullable = false)
    public int stock;

    @Column(nullable = false)
    public int reorderPoint;

    @Column(nullable = false)
    public boolean discontinued;

    public boolean needsRestock() {
        return !discontinued && stock <= reorderPoint;
    }

    public interface Repo extends PanacheRepository<Product> {

        @Find
        Optional<Product> findBySku(String sku);

        @HQL("where category = :category and discontinued = false order by name")
        List<Product> findAvailableByCategory(String category);

        @HQL("where name like :pattern and discontinued = false order by name")
        List<Product> searchByName(String pattern);

        @HQL("where stock <= reorderPoint and discontinued = false order by stock, sku")
        List<Product> findLowStock();

        @HQL("delete from Product where discontinued = true")
        long deleteDiscontinued();
    }

    public interface InventoryRepo extends PanacheRepository.Stateless<Product, Long> {

        @Find
        Optional<Product> findBySku(String sku);

        @HQL("update Product set stock = stock + :delta where sku = :sku and discontinued = false")
        long changeStock(String sku, int delta);
    }
}
