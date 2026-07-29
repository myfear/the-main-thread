package com.themainthread.flyway.api;

import com.themainthread.flyway.domain.Customer;
import com.themainthread.flyway.persistence.CustomerRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/customers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CustomerResource {

    private final CustomerRepository repository;

    @Inject
    public CustomerResource(CustomerRepository repository) {
        this.repository = repository;
    }

    @POST
    public Response create(@Valid CreateCustomerRequest request) {
        Customer customer = repository.create(request.email(), request.displayName());
        return Response.status(Response.Status.CREATED).entity(customer).build();
    }

    @GET
    @Path("/{id}")
    public Customer findById(@PathParam("id") long id) {
        return repository.findById(id).orElseThrow(NotFoundException::new);
    }

    @PUT
    @Path("/{id}/name")
    public Customer rename(@PathParam("id") long id, @Valid RenameCustomerRequest request) {
        return repository.rename(id, request.displayName()).orElseThrow(NotFoundException::new);
    }
}
