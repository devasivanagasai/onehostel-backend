package com.onehostel.common.advice;

import com.onehostel.common.dto.CommonRequestObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unwraps {"requestObject": {...}, "clientSource": ...} so a controller binds its real type:
 * {@code create(@Valid @RequestBody RoomRequest room)}. The other envelope fields stay available via
 * RequestEnvelope.current(). A parameter typed CommonRequestObject gets the whole envelope; a body
 * without requestObject is bound as-is.
 */
@RestControllerAdvice(basePackages = "com.onehostel")
public class RequestEnvelopeAdvice extends RequestBodyAdviceAdapter {

    private static final String REQUEST_OBJECT = "requestObject";

    private final ObjectMapper objectMapper;

    public RequestEnvelopeAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)
                && !CommonRequestObject.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {

        byte[] body = inputMessage.getBody().readAllBytes();
        if (body.length > 0) {
            JsonNode root = objectMapper.readTree(body);
            if (root instanceof ObjectNode envelope && envelope.has(REQUEST_OBJECT)) {
                JsonNode requestObject = envelope.remove(REQUEST_OBJECT);
                RequestEnvelope.store(objectMapper.treeToValue(envelope, CommonRequestObject.class));
                body = objectMapper.writeValueAsBytes(requestObject);
            }
        }
        return new BufferedInputMessage(inputMessage.getHeaders(), body);
    }

    private record BufferedInputMessage(HttpHeaders headers, byte[] body) implements HttpInputMessage {

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
