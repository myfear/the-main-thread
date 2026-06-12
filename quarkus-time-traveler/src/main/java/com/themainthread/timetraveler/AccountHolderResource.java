package com.themainthread.timetraveler;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.hibernate.audit.AuditEntry;
import org.hibernate.audit.ModificationType;

@Path("/holders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AccountHolderResource {

    private final AccountHolderService accountHolderService;

    public AccountHolderResource(AccountHolderService accountHolderService) {
        this.accountHolderService = accountHolderService;
    }

    @POST
    public Response create(CreateAccountHolderRequest request) {
        AccountHolder holder = accountHolderService.create(
                request.externalId(),
                request.fullName(),
                request.email(),
                request.kycStatus());
        return Response.status(Response.Status.CREATED).entity(toView(holder)).build();
    }

    @PUT
    @Path("/{id}")
    public AccountHolderView update(@PathParam("id") Long id, UpdateAccountHolderRequest request) {
        return toView(accountHolderService.update(id, request.fullName(), request.email(), request.kycStatus()));
    }

    @GET
    @Path("/{id}/audit")
    public List<AccountHolderAuditItem> audit(@PathParam("id") Long id) {
        return accountHolderService.getHistory(id).stream()
                .map(AccountHolderResource::toAuditItem)
                .toList();
    }

    private static AccountHolderView toView(AccountHolder holder) {
        return new AccountHolderView(
                holder.getId(),
                holder.getExternalId(),
                holder.getFullName(),
                holder.getEmail(),
                holder.getKycStatus());
    }

    private static AccountHolderAuditItem toAuditItem(AuditEntry<AccountHolder> entry) {
        LedgerRevision revision = (LedgerRevision) entry.changeset();
        long changesetId = revision.getId();
        Instant changedAt = revision.getRevisionInstant();
        Set<String> modifiedEntities =
                revision.getModifiedEntityNames() == null ? Set.of() : revision.getModifiedEntityNames();

        return new AccountHolderAuditItem(
                changesetId,
                changedAt,
                entry.modificationType(),
                modifiedEntities,
                toView(entry.entity()));
    }
}

record CreateAccountHolderRequest(String externalId, String fullName, String email, KycStatus kycStatus) {
}

record UpdateAccountHolderRequest(String fullName, String email, KycStatus kycStatus) {
}

record AccountHolderView(Long id, String externalId, String fullName, String email, KycStatus kycStatus) {
}

record AccountHolderAuditItem(
        long changesetId,
        Instant changedAt,
        ModificationType modificationType,
        Set<String> modifiedEntities,
        AccountHolderView holder) {
}
