package com.themainthread.timetraveler;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

    private final AccountService accountService;

    public AccountResource(AccountService accountService) {
        this.accountService = accountService;
    }

    @POST
    public Response create(CreateAccountRequest request) {
        Account account = accountService.create(request.accountNumber(), request.openingBalance());
        return Response.status(Response.Status.CREATED).entity(toView(account)).build();
    }

    @GET
    @Path("/{id}")
    public AccountView getCurrent(@PathParam("id") Long id) {
        return toView(accountService.getCurrent(id));
    }

    @GET
    @Path("/{id}/snapshot")
    public AccountView getSnapshot(@PathParam("id") Long id, @QueryParam("asOf") String asOf) {
        return toView(accountService.getSnapshot(id, Instant.parse(asOf)));
    }

    @PUT
    @Path("/{id}/balance")
    public AccountView changeBalance(@PathParam("id") Long id, BalanceUpdateRequest request) {
        return toView(accountService.changeBalance(id, request.balance(), request.status()));
    }

    private static AccountView toView(Account account) {
        return new AccountView(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getStatus());
    }
}

record CreateAccountRequest(String accountNumber, BigDecimal openingBalance) {
}

record BalanceUpdateRequest(BigDecimal balance, AccountStatus status) {
}

record AccountView(Long id, String accountNumber, BigDecimal balance, AccountStatus status) {
}
