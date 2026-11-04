package com.onehostel.common.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onehostel.common.advice.RequestEnvelope;
import com.onehostel.common.annotation.ResponseMessage;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.GlobalExceptionHandler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** The request envelope is unwrapped into the typed body, and every response comes back in the frontend's shape. */
class EnvelopeAdviceTest {

    record Greeting(@NotBlank String name) {
    }

    @RestController
    static class GreetingController {

        @PostMapping("/greet")
        @ResponseMessage("Greeted")
        Greeting greet(@Valid @RequestBody Greeting greeting) {
            return new Greeting("Hello " + greeting.name() + " via " + RequestEnvelope.clientSource().orElse(null));
        }

        @PostMapping("/missing")
        Greeting missing(@RequestBody Greeting greeting) {
            throw CommonErrorCode.ENTITY_NOT_FOUND.raise("Greeting", greeting.name());
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(new GreetingController())
                .setControllerAdvice(new RequestEnvelopeAdvice(objectMapper), new ResponseEnvelopeAdvice(),
                        new GlobalExceptionHandler("test-service"))
                .build();
    }

    @Test
    void unwrapsRequestObjectAndWrapsTheResult() throws Exception {
        mvc.perform(post("/greet").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestObject\":{\"name\":\"Priya\"},\"clientSource\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Greeted"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.responseObject.name").value("Hello Priya via WEB"));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(post("/greet").contentType(MediaType.APPLICATION_JSON).content("{\"requestObject\":{\"name\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void domainErrorsUseHonestStatuses() throws Exception {
        mvc.perform(post("/missing").contentType(MediaType.APPLICATION_JSON).content("{\"requestObject\":{\"name\":\"x\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Greeting not found: x"))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/greet").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }
}
