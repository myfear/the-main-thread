package com.mainthread.audit.api;

import com.mainthread.audit.domain.CreditAccount;
import com.mainthread.audit.service.CreditAccountService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

@Path("/accounts")
@Consumes("application/json")
@Produces("application/json")
public class CreditAccountResource {

    @Inject
    CreditAccountService service;

    @POST
    public CreditAccount create(CreateAccountRequest request) {
        return service.create(request.owner, request.limit);
    }

    @PUT
    @Path("{id}/limit")
    public CreditAccount updateLimit(@PathParam("id") Long id, UpdateLimitRequest request) {
        return service.updateLimit(id, request.limit);
    }

    @POST
    @Path("{id}/suspend")
    public CreditAccount suspend(@PathParam("id") Long id) {
        return service.suspend(id);
    }

    public static class CreateAccountRequest {
        public String owner;
        public long limit;
    }

    public static class UpdateLimitRequest {
        public long limit;
    }
}