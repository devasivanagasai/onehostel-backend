package com.onehostel.common.advice;

import java.time.Instant;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.onehostel.common.annotation.ResponseMessage;
import com.onehostel.common.dto.ApiErrorResponse;
import com.onehostel.common.dto.CommonResponseObject;
import com.onehostel.common.helper.CorrelationContext;

/**
 * Wraps every JSON body a OneHostel controller returns in {success, message, responseObject, statusCode},
 * so controllers return real types. Bodies that already are an envelope or an ApiErrorResponse pass through.
 * The message comes from @ResponseMessage, else "Success".
 */
@RestControllerAdvice(basePackages = "com.onehostel")
public class ResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    private static final String DEFAULT_MESSAGE = "Success";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {

        if (body instanceof CommonResponseObject<?> || body instanceof ApiErrorResponse) {
            return body;
        }
        int status = response instanceof ServletServerHttpResponse servlet ? servlet.getServletResponse().getStatus() : 200;
        ResponseMessage message = returnType.getMethodAnnotation(ResponseMessage.class);
        return new CommonResponseObject<>(true, message != null ? message.value() : DEFAULT_MESSAGE, body, status,
                CorrelationContext.current(), Instant.now());
    }
}
