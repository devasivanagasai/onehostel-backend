package com.onehostel.common.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.common.dto.CommonResponseObject;
import com.onehostel.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        CommonResponseObject<Void> body = CommonResponseObject.error(code, message, null);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code, code.format());
    }
}
