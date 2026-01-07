package com.example;

import java.util.List;
import java.util.Map;

import com.example.entity.Product;
import com.example.flags.FeatureFlag;
import com.example.service.ProductService;

import io.quarkiverse.flags.Flags;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @Inject
    ProductService productService;

    @Inject
    Flags flags;

    @GET
    public List<Product> getProducts() {
        return productService.getProductsWithDetails();
    }

    @GET
    @Path("/bulk-update")
    public Response bulkUpdatePrices(@QueryParam("multiplier") Double multiplier) {
        if (multiplier == null || multiplier <= 0) {
            return Response.status(400)
                    .entity(Map.of("error", "Invalid multiplier"))
                    .build();
        }

        try {
            List<Product> updated = productService.bulkUpdatePrices(multiplier);
            return Response.ok(updated).build();
        } catch (IllegalStateException e) {
            return Response.status(403)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/features")
    public Map<String, Boolean> getFeatureStatus() {
        return Map.of(
                "premium-features", safeIsEnabled("premium-features"),
                "bulk-operations", safeIsEnabled("bulk-operations"),
                "analytics-dashboard", safeIsEnabled("analytics-dashboard"),
                "new-ui", safeIsEnabled("new-ui"),
                "spring-sale", safeIsEnabled("spring-sale"));
    }

    @PUT
    @Path("/features/bulk-operations/toggle")
    @Transactional
    public Response toggleBulkOperations() {
        FeatureFlag flag = FeatureFlag.find("feature", "bulk-operations").firstResult();
        
        if (flag == null) {
            return Response.status(404)
                    .entity(Map.of("error", "bulk-operations flag not found"))
                    .build();
        }
        
        // Toggle the value
        boolean currentValue = "true".equalsIgnoreCase(flag.value);
        flag.value = currentValue ? "false" : "true";
        flag.persist();
        
        return Response.ok(Map.of(
                "flag", "bulk-operations",
                "enabled", !currentValue,
                "message", "Flag toggled successfully"))
                .build();
    }

    private boolean safeIsEnabled(String flagName) {
        try {
            return flags.isEnabled(flagName);
        } catch (Exception e) {
            return false;
        }
    }
}