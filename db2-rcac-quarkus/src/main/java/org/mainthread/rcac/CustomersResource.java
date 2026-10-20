package org.mainthread.rcac;

import java.sql.SQLException;
import java.util.List;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;

@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Blocking
public class CustomersResource {
    private final SecurityIdentity identity;
    private final CustomerRepository customers;

    public CustomersResource(SecurityIdentity identity, CustomerRepository customers) {
        this.identity = identity;
        this.customers = customers;
    }

    @GET
    public List<Customer> list() throws SQLException {
        return customers.list(pool());
    }

    @GET
    @Path("/{id}")
    public Response find(@PathParam("id") long id) throws SQLException {
        return customers.find(pool(), id)
                .map(customer -> Response.ok(customer).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiError("Customer not found")).build());
    }

    private PoolIdentity pool() {
        return PoolIdentity.forPrincipal(identity.getPrincipal().getName())
                .orElseThrow(ForbiddenException::new);
    }

    public record ApiError(String message) {
    }
}
