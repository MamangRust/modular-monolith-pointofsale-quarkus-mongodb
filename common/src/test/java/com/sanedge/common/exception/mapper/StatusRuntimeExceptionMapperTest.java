package com.sanedge.common.exception.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.ws.rs.core.Response;

class StatusRuntimeExceptionMapperTest {

    private final StatusRuntimeExceptionMapper mapper = new StatusRuntimeExceptionMapper();

    @Test
    void mapsResourceExhaustedToHttp429() {
        StatusRuntimeException ex = Status.RESOURCE_EXHAUSTED
                .withDescription("Rate limit exceeded")
                .asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertEquals(429, response.getStatus());
    }

    @Test
    void mapsNotFoundToHttp404() {
        StatusRuntimeException ex = Status.NOT_FOUND
                .withDescription("Order not found")
                .asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertEquals(404, response.getStatus());
    }

    @Test
    void mapsUnavailableToHttp503() {
        StatusRuntimeException ex = Status.UNAVAILABLE
                .withDescription("service down")
                .asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertEquals(503, response.getStatus());
    }

    @Test
    void usesCodeNameWhenDescriptionMissing() {
        StatusRuntimeException ex = Status.RESOURCE_EXHAUSTED.asRuntimeException();

        Response response = mapper.toResponse(ex);
        String body = response.getEntity().toString();

        assertTrue(body.contains("RESOURCE_EXHAUSTED"));
    }

    @Test
    void fallsBackToHttp500ForUnknownCode() {
        StatusRuntimeException ex = Status.UNKNOWN.asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertEquals(500, response.getStatus());
    }
}
