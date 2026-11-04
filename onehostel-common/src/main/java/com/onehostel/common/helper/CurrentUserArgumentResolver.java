package com.onehostel.common.helper;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.onehostel.common.annotation.AuthenticatedUser;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.vo.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves {@code @AuthenticatedUser CurrentUser} parameters. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
                && parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentUser user = CurrentUser.from(webRequest.getNativeRequest(HttpServletRequest.class));
        if (!user.isAuthenticated()) {
            throw CommonErrorCode.UNAUTHORIZED.raise();
        }
        return user;
    }
}
