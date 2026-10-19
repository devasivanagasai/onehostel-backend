package com.onehostel.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdRequest(@NotBlank @Size(max = 64) String id) {
}
