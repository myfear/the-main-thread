package dev.mainthread.fieldnotes;

import java.util.Map;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

public class Problem extends RuntimeException {
    final int status;
    public Problem(int status, String message) { super(message); this.status = status; }

    @Provider
    public static class Mapper implements ExceptionMapper<Problem> {
        @Override
        public Response toResponse(Problem problem) {
            return Response.status(problem.status).entity(Map.of("message", problem.getMessage())).build();
        }
    }

    @Provider
    public static class InputMapper implements ExceptionMapper<IllegalArgumentException> {
        @Override
        public Response toResponse(IllegalArgumentException problem) {
            return Response.status(400).entity(Map.of("message", problem.getMessage() == null ? "Invalid input" : problem.getMessage())).build();
        }
    }
}
