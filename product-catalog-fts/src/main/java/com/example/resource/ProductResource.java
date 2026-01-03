package com.example.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.dto.PageResponse;
import com.example.dto.ProductDTO;
import com.example.entity.Product;
import com.example.pagination.CursorCodec;
import com.example.pagination.CursorPayload;
import com.example.repository.ProductRepository;

import io.quarkus.logging.Log;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    private final ProductRepository repo;
    private final CursorCodec codec;

    public ProductResource(ProductRepository repo, CursorCodec codec) {
        this.repo = repo;
        this.codec = codec;
    }

    @GET
    public PageResponse<ProductDTO> list(
            @QueryParam("category") String category,
            @QueryParam("q") String q,
            @QueryParam("sortBy") @DefaultValue("popularity") String sortBy,
            @QueryParam("cursor") String cursor,
            @QueryParam("direction") @DefaultValue("next") String direction,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        System.out.println("=== API Request ===");
        System.out.println("direction: " + direction);
        System.out.println("sortBy: " + sortBy);
        System.out.println("category: " + category);
        System.out.println("q: " + q);
        System.out.println(
                "cursor: " + (cursor != null ? cursor.substring(0, Math.min(20, cursor.length())) + "..." : "null"));

        limit = Math.min(limit, 100);
        boolean forward = !"prev".equalsIgnoreCase(direction);

        CursorPayload decoded = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                decoded = codec.decode(cursor);
                System.out.println("Decoded cursor: sort=" + decoded.sort() + ", category=" + decoded.category()
                        + ", q=" + decoded.q() + ", direction=" + decoded.direction());
            } catch (IllegalArgumentException e) {
                System.err.println("Failed to decode cursor: " + e.getMessage());
                e.printStackTrace();
                throw new BadRequestException("Invalid cursor: " + e.getMessage());
            }

            // When a cursor is provided, use the sort method from the cursor
            // This handles cases where the backend switched to relevance sorting for
            // searches
            sortBy = decoded.sort();

            if (safe(category).equals(decoded.category()) == false
                    || safe(q).equals(decoded.q()) == false) {
                String errorMsg = String.format(
                        "Cursor does not match request context. Expected: category=%s, q=%s. Got: category=%s, q=%s",
                        safe(category), safe(q), decoded.category(), decoded.q());
                System.err.println(errorMsg);
                throw new BadRequestException(errorMsg);
            }
        }

        Log.infof("Validation passed, sortBy=" + sortBy + ", proceeding with query...");

        List<ProductDTO> dtos = new ArrayList<>();
        String nextCursor = null;
        String prevCursor = null;

        if (q != null && !q.isBlank()) {
            Long rk = decoded == null ? null : decoded.k1();
            Long id = decoded == null ? null : decoded.k2();

            List<Object[]> rows = repo.searchByRelevance(category, q, rk, id, forward, limit + 1);

            boolean hasMore = rows.size() > limit;
            if (hasMore) {
                rows = rows.subList(0, limit);
            }

            for (Object[] row : rows) {
                // Native query returns columns; Product is first in our fetch pattern only in
                // typed queries.
                // Here we re-fetch rows as raw, so map manually.
                // Column order from SQL: id, name, description, category, price, view_count,
                // created_at, image_url, rank_scaled
                Product p = new Product();
                p.id = ((Number) row[0]).longValue();
                p.name = (String) row[1];
                p.description = (String) row[2];
                p.category = (String) row[3];
                p.price = row[4] == null ? null : ((Number) row[4]).doubleValue();
                p.viewCount = row[5] == null ? 0 : ((Number) row[5]).intValue();

                // Handle created_at - can be either Timestamp or Instant depending on driver
                if (row[6] == null) {
                    p.createdAt = Instant.EPOCH;
                } else if (row[6] instanceof java.sql.Timestamp) {
                    p.createdAt = ((java.sql.Timestamp) row[6]).toInstant();
                } else if (row[6] instanceof Instant) {
                    p.createdAt = (Instant) row[6];
                } else {
                    p.createdAt = Instant.EPOCH;
                }

                p.imageUrl = (String) row[7];

                long rankScaled = ((Number) row[8]).longValue();

                String itemCursor = codec.encode(new CursorPayload(
                        "relevance", safe(category), safe(q), safe(direction), rankScaled, p.id));

                dtos.add(toDTO(p, itemCursor));
            }

            if (!dtos.isEmpty()) {
                ProductDTO first = dtos.get(0);
                ProductDTO last = dtos.get(dtos.size() - 1);

                prevCursor = hasBackCursor("relevance", category, q, first.cursor())
                        ? flipDirection(first.cursor())
                        : null;

                nextCursor = hasMore ? last.cursor() : null;

                return new PageResponse<>(dtos, nextCursor, prevCursor, nextCursor != null, prevCursor != null,
                        dtos.size());
            }

            return new PageResponse<>(dtos, null, null, false, decoded != null, 0);
        }

        if ("newest".equalsIgnoreCase(sortBy)) {
            Long ts = decoded == null ? null : decoded.k1();
            Long id = decoded == null ? null : decoded.k2();

            List<Product> products = repo.pageByNewest(category, ts, id, forward, limit + 1);
            boolean hasMore = products.size() > limit;
            if (hasMore) {
                products = products.subList(0, limit);
            }

            for (Product p : products) {
                long k1 = p.createdAt.getEpochSecond();
                String itemCursor = codec.encode(new CursorPayload(
                        "newest", safe(category), safe(q), safe(direction), k1, p.id));
                dtos.add(toDTO(p, itemCursor));
            }

            if (!dtos.isEmpty()) {
                prevCursor = flipDirection(dtos.get(0).cursor());
                nextCursor = hasMore ? dtos.get(dtos.size() - 1).cursor() : null;
            }

            return new PageResponse<>(dtos, nextCursor, prevCursor, nextCursor != null, decoded != null && forward,
                    dtos.size());
        }

        Long vc = decoded == null ? null : decoded.k1();
        Long id = decoded == null ? null : decoded.k2();

        List<Product> products = repo.pageByPopularity(category, vc, id, forward, limit + 1);
        boolean hasMore = products.size() > limit;
        if (hasMore) {
            products = products.subList(0, limit);
        }

        for (Product p : products) {
            long k1 = p.viewCount.longValue();
            String itemCursor = codec.encode(new CursorPayload(
                    "popularity", safe(category), safe(q), safe(direction), k1, p.id));
            dtos.add(toDTO(p, itemCursor));
        }

        if (!dtos.isEmpty()) {
            prevCursor = flipDirection(dtos.get(0).cursor());
            nextCursor = hasMore ? dtos.get(dtos.size() - 1).cursor() : null;
        }

        return new PageResponse<>(dtos, nextCursor, prevCursor, nextCursor != null, decoded != null && forward,
                dtos.size());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private ProductDTO toDTO(Product p, String cursor) {
        return new ProductDTO(
                p.id,
                p.name,
                p.description,
                p.category,
                p.price,
                p.viewCount,
                p.createdAt == null ? null : p.createdAt.toString(),
                p.imageUrl,
                cursor);
    }

    private boolean hasBackCursor(String sort, String category, String q, String cursor) {
        return cursor != null && !cursor.isBlank();
    }

    private String flipDirection(String cursor) {
        CursorPayload payload = codec.decode(cursor);
        String flipped = "prev".equalsIgnoreCase(payload.direction()) ? "next" : "prev";
        return codec.encode(new CursorPayload(
                payload.sort(), payload.category(), payload.q(), flipped, payload.k1(), payload.k2()));
    }

    @POST
    @Path("/seed")
    @Transactional
    public String seed(@QueryParam("count") @DefaultValue("5000") int count) {
        repo.deleteAll();

        String[] categories = { "Electronics", "Books", "Clothing", "Home", "Sports" };
        String[] adjectives = { "Premium", "Budget", "Luxury", "Essential", "Professional" };
        String[] items = { "Widget", "Gadget", "Tool", "Device", "Kit" };
        String[] phrases = {
                "wireless noise cancelling battery life",
                "durable lightweight travel friendly",
                "ergonomic adjustable premium materials",
                "high performance professional grade",
                "compact minimalist modern design"
        };

        for (int i = 0; i < count; i++) {
            Product p = new Product();
            p.name = adjectives[i % adjectives.length] + " " + items[i % items.length] + " " + i;
            p.description = "A " + phrases[i % phrases.length] + " product designed for "
                    + categories[i % categories.length] + " lovers.";
            p.category = categories[i % categories.length];
            p.price = 10.0 + (i % 500);
            p.viewCount = (int) (Math.random() * 10000);
            p.createdAt = Instant.now().minusSeconds(i * 37L);
            p.imageUrl = "https://example.com/image" + i + ".jpg";
            p.persist();
        }

        return "Seeded " + count + " products";
    }
}