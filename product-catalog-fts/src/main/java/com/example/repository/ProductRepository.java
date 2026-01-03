package com.example.repository;

import com.example.entity.Product;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    private final EntityManager em;

    public ProductRepository(EntityManager em) {
        this.em = em;
    }

    public List<Product> pageByPopularity(String category, Long viewCount, Long id, boolean forward, int limit) {
        String baseWhere = category == null || category.isBlank() ? "1=1" : "p.category = :category";

        String seek;
        String order;
        if (forward) {
            seek = (viewCount == null || id == null)
                    ? ""
                    : " AND ((p.viewCount < :vc) OR (p.viewCount = :vc AND p.id > :id))";
            order = " ORDER BY p.viewCount DESC, p.id ASC";
        } else {
            seek = (viewCount == null || id == null)
                    ? ""
                    : " AND ((p.viewCount > :vc) OR (p.viewCount = :vc AND p.id < :id))";
            order = " ORDER BY p.viewCount ASC, p.id DESC";
        }

        String jpql = "SELECT p FROM Product p WHERE " + baseWhere + seek + order;

        var q = em.createQuery(jpql, Product.class);
        if (category != null && !category.isBlank()) {
            q.setParameter("category", category);
        }
        if (viewCount != null && id != null) {
            q.setParameter("vc", viewCount.intValue());
            q.setParameter("id", id);
        }
        q.setMaxResults(limit);
        List<Product> list = q.getResultList();

        if (!forward) {
            java.util.Collections.reverse(list);
        }
        return list;
    }

    public List<Product> pageByNewest(String category, Long createdAtEpoch, Long id, boolean forward, int limit) {
        String baseWhere = category == null || category.isBlank() ? "1=1" : "p.category = :category";

        String seek;
        String order;
        if (forward) {
            seek = (createdAtEpoch == null || id == null)
                    ? ""
                    : " AND ((p.createdAt < :ts) OR (p.createdAt = :ts AND p.id < :id))";
            order = " ORDER BY p.createdAt DESC, p.id DESC";
        } else {
            seek = (createdAtEpoch == null || id == null)
                    ? ""
                    : " AND ((p.createdAt > :ts) OR (p.createdAt = :ts AND p.id > :id))";
            order = " ORDER BY p.createdAt ASC, p.id ASC";
        }

        String jpql = "SELECT p FROM Product p WHERE " + baseWhere + seek + order;

        var q = em.createQuery(jpql, Product.class);
        if (category != null && !category.isBlank()) {
            q.setParameter("category", category);
        }
        if (createdAtEpoch != null && id != null) {
            q.setParameter("ts", Timestamp.from(Instant.ofEpochSecond(createdAtEpoch)));
            q.setParameter("id", id);
        }
        q.setMaxResults(limit);
        List<Product> list = q.getResultList();

        if (!forward) {
            java.util.Collections.reverse(list);
        }
        return list;
    }

    /**
     * Full-text search with keyset pagination, ordered by relevance first.
     *
     * We scale rank into an integer so cursors stay deterministic:
     * rankScaled = round(ts_rank_cd(...) * 1_000_000)
     */
    public List<Object[]> searchByRelevance(String category, String qText, Long rankScaled, Long id, boolean forward,
            int limit) {
        String baseWhere = category == null || category.isBlank() ? "TRUE" : "p.category = :category";

        String seek;
        String order;
        if (forward) {
            seek = (rankScaled == null || id == null)
                    ? ""
                    : " AND ((r.rank_scaled < :rk) OR (r.rank_scaled = :rk AND p.id > :id))";
            order = " ORDER BY r.rank_scaled DESC, p.id ASC";
        } else {
            seek = (rankScaled == null || id == null)
                    ? ""
                    : " AND ((r.rank_scaled > :rk) OR (r.rank_scaled = :rk AND p.id < :id))";
            order = " ORDER BY r.rank_scaled ASC, p.id DESC";
        }

        String sql = """
                WITH r AS (
                  SELECT
                    p.id,
                    ROUND(ts_rank_cd(
                      to_tsvector('english', p.name || ' ' || COALESCE(p.description, '')),
                      plainto_tsquery('english', :q)
                    ) * 1000000)::bigint AS rank_scaled
                  FROM products p
                  WHERE %s
                    AND to_tsvector('english', p.name || ' ' || COALESCE(p.description, ''))
                        @@ plainto_tsquery('english', :q)
                )
                SELECT p.id, p.name, p.description, p.category, p.price, p.view_count, p.created_at, p.image_url, r.rank_scaled
                FROM products p
                JOIN r ON r.id = p.id
                WHERE %s %s
                %s
                """
                .formatted(baseWhere, baseWhere, seek, order);

        Query query = em.createNativeQuery(sql, Product.class);
        query.setParameter("q", qText);

        if (category != null && !category.isBlank()) {
            query.setParameter("category", category);
        }
        if (rankScaled != null && id != null) {
            query.setParameter("rk", rankScaled);
            query.setParameter("id", id);
        }

        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Product> products = query.getResultList();

        // We still need rank_scaled in the response for cursor generation.
        // So we rerun as Object[] only for the returned ids, which is small.
        if (products.isEmpty()) {
            return List.of();
        }

        String idList = products.stream().map(p -> p.id.toString()).reduce((a, b) -> a + "," + b).orElseThrow();
        String sqlRankFetch = """
                SELECT p.id, p.name, p.description, p.category, p.price, p.view_count, p.created_at, p.image_url,
                ROUND(ts_rank_cd(
                  to_tsvector('english', p.name || ' ' || COALESCE(p.description, '')),
                  plainto_tsquery('english', :q)
                ) * 1000000)::bigint AS rank_scaled
                FROM products p
                WHERE p.id IN (%s)
                %s
                """.formatted(idList, order.replace("r.rank_scaled", "rank_scaled"));

        Query q2 = em.createNativeQuery(sqlRankFetch);
        q2.setParameter("q", qText);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q2.getResultList();

        if (!forward) {
            java.util.Collections.reverse(rows);
        }
        return rows;
    }
}