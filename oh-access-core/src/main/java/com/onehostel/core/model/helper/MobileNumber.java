package com.onehostel.core.model.helper;

import java.io.Serializable;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileNumber implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Country code is required")
    @Field("MN0001")
    private String countryCode;

    @NotBlank(message = "Number is required")
    // Same rule as GlobalConstants.PHONE_REGEX, allowing spaces or dashes between digits
    @Pattern(regexp = "^[6-9](?:[\\s-]?\\d){9}$", message = "Mobile number must be 10 digits starting with 6-9")
    @Field("MN0002")
    private String number;
}