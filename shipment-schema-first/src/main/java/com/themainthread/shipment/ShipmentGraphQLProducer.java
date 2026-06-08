package com.themainthread.shipment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class ShipmentGraphQLProducer {

    private final ShipmentStore shipmentStore;

    ShipmentGraphQLProducer(ShipmentStore shipmentStore) {
        this.shipmentStore = shipmentStore;
    }

    @Produces
    @Singleton
    GraphQL graphQL() {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(loadSchema());
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Query")
                        .dataFetcher("viewerWarehouse", viewerWarehouse())
                        .dataFetcher("shipments", shipments())
                        .dataFetcher("shipment", shipment()))
                .type(TypeRuntimeWiring.newTypeWiring("Mutation")
                        .dataFetcher("updateShipmentStatus", updateShipmentStatus()))
                .type(TypeRuntimeWiring.newTypeWiring("Subscription")
                        .dataFetcher("shipmentUpdates", shipmentUpdates()))
                .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private DataFetcher<String> viewerWarehouse() {
        return environment -> currentWarehouse(environment);
    }

    private DataFetcher<Object> shipments() {
        return environment -> shipmentStore.listShipments(
                currentWarehouse(environment),
                statusArgument(environment, "status"));
    }

    private DataFetcher<Shipment> shipment() {
        return environment -> shipmentStore.findShipment(
                environment.getArgument("id"),
                currentWarehouse(environment));
    }

    private DataFetcher<Shipment> updateShipmentStatus() {
        return environment -> shipmentStore.updateStatus(
                environment.getArgument("id"),
                statusArgument(environment, "status"),
                currentWarehouse(environment));
    }

    private DataFetcher<Object> shipmentUpdates() {
        return environment -> shipmentStore.shipmentUpdates(environment.getArgument("id"));
    }

    private String currentWarehouse(graphql.schema.DataFetchingEnvironment environment) {
        RoutingContext routingContext = GraphQLHandler.getRoutingContext(environment.getGraphQlContext());
        if (routingContext == null) {
            return ShipmentStore.DEFAULT_WAREHOUSE;
        }

        return Optional.ofNullable(routingContext.request().getHeader("X-Warehouse-Code"))
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .orElse(ShipmentStore.DEFAULT_WAREHOUSE);
    }

    private ShipmentStatus statusArgument(graphql.schema.DataFetchingEnvironment environment, String name) {
        Object value = environment.getArgument(name);
        if (value == null) {
            return null;
        }
        if (value instanceof ShipmentStatus shipmentStatus) {
            return shipmentStatus;
        }
        return ShipmentStatus.valueOf(value.toString());
    }

    private String loadSchema() {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("graphql/shipment.graphqls")) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not load graphql/shipment.graphqls");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
