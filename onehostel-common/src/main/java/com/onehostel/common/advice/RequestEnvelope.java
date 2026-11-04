package com.onehostel.common.advice;

import com.onehostel.common.constants.CommonEnums.ClientSource;
import com.onehostel.common.dto.CommonRequestObject;

import java.util.Optional;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The envelope fields (clientSource, paging, ...) of the current request, after RequestEnvelopeAdvice
 * has handed the controller only its requestObject. requestObject itself is null here.
 */
public final class RequestEnvelope {

    static final String ATTRIBUTE = RequestEnvelope.class.getName();

    private RequestEnvelope() {
    }

    public static Optional<CommonRequestObject<?>> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((CommonRequestObject<?>) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }

    public static Optional<ClientSource> clientSource() {
        return current().map(CommonRequestObject::clientSource);
    }

    static void store(CommonRequestObject<?> envelope) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(ATTRIBUTE, envelope, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
