package io.mainthread.catalogboard;

import java.net.URI;
import java.util.List;

import io.quarkus.hibernate.panache.blocking.PanacheBlockingQuery;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @Inject
    Product.Repo repo;

    @Inject
    Product.InventoryRepo inventoryRepo;

    @GET
    public List<ProductResponse> list(
            @QueryParam("category") String category,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        PanacheBlockingQuery<Product> query = category == null || category.isBlank()
                ? repo.find("discontinued = false", Order.by(Sort.asc("name")))
                : repo.find("category = ?1 and discontinued = false", Order.by(Sort.asc("name")), category);

        return query.page(page, size)
                .list()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GET
    @Path("/{sku}")
    public ProductResponse get(@PathParam("sku") String sku) {
        return ProductResponse.from(requireProduct(sku));
    }

    @GET
    @Path("/search")
    public List<ProductResponse> search(@QueryParam("q") @DefaultValue("") String query) {
        if (query.isBlank()) {
            return List.of();
        }
        return repo.searchByName("%" + query + "%")
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GET
    @Path("/low-stock")
    public List<ProductResponse> lowStock() {
        return repo.findLowStock()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @POST
    @Transactional
    public Response create(@Valid ProductCreateRequest request) {
        if (repo.findBySku(request.sku()).isPresent()) {
            throw new BadRequestException("SKU already exists: " + request.sku());
        }

        Product product = new Product();
        product.sku = request.sku();
        product.name = request.name();
        product.category = request.category();
        product.stock = request.stock();
        product.reorderPoint = request.reorderPoint();
        repo.persist(product);

        return Response.created(URI.create("/products/" + product.sku))
                .entity(ProductResponse.from(product))
                .build();
    }

    @PUT
    @Path("/{sku}")
    @Transactional
    public ProductResponse update(@PathParam("sku") String sku, @Valid ProductUpdateRequest request) {
        Product product = requireProduct(sku);
        product.name = request.name();
        product.category = request.category();
        product.reorderPoint = request.reorderPoint();
        return ProductResponse.from(product);
    }

    @PATCH
    @Path("/{sku}/stock")
    @Transactional
    public ProductResponse changeStock(@PathParam("sku") String sku, @Valid StockAdjustment adjustment) {
        long updated = inventoryRepo.changeStock(sku, adjustment.delta());
        if (updated == 0) {
            throw new NotFoundException("Active product not found: " + sku);
        }
        return ProductResponse.from(inventoryRepo.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Product not found after stock change: " + sku)));
    }

    @DELETE
    @Path("/{sku}")
    @Transactional
    public Response discontinue(@PathParam("sku") String sku) {
        Product product = requireProduct(sku);
        product.discontinued = true;
        return Response.noContent().build();
    }

    @DELETE
    @Path("/maintenance/discontinued")
    @Transactional
    public Response deleteDiscontinued() {
        long deleted = repo.deleteDiscontinued();
        return Response.ok().entity(new DeletedProducts(deleted)).build();
    }

    private Product requireProduct(String sku) {
        Product product = repo.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Product not found: " + sku));
        if (product.discontinued) {
            throw new NotFoundException("Product not found: " + sku);
        }
        return product;
    }

    public record DeletedProducts(long deleted) {
    }
}
