package com.catalogapi;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.catalogapi.json.BundleView;
import com.catalogapi.json.CatalogPayload;
import com.catalogapi.json.ProductView;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/catalog/payloads")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog payloads")
public class CatalogPayloadResource {

    @GET
    @Path("/demo")
    @Operation(summary = "Polymorphic catalog payloads for contract testing")
    public List<CatalogPayload> demo() {
        Product keyboard = Product.find("sku", "SKU-001").firstResult();
        Product hub = Product.find("sku", "SKU-002").firstResult();
        Product bundle = Product.find("sku", "SKU-004").firstResult();

        return List.of(
                new ProductView(keyboard.id, keyboard.sku, ProductMapper.toMoney(keyboard)),
                new ProductView(hub.id, hub.sku, ProductMapper.toMoney(hub)),
                new BundleView(
                        bundle.name,
                        List.of("SKU-001", "SKU-002"),
                        ProductMapper.toMoney(bundle)));
    }
}
