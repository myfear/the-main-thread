package com.themainthread.shipment;

import graphql.GraphQL;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import io.vertx.ext.web.handler.graphql.ws.GraphQLWSHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ShipmentGraphQLRoutes {

    void register(@Observes Router router, GraphQL graphQL) {
        router.post("/graphql")
                .handler(BodyHandler.create());
        router.route("/graphql")
                .handler(GraphQLWSHandler.create(graphQL));
        router.route("/graphql")
                .handler(GraphQLHandler.create(graphQL));
    }
}
