package com.onehostel.common.dto;

import jakarta.validation.constraints.Size;

public record ListFilter(@Size(max = 100) String keyword, @Size(max = 64) String hostelBranchId) {
}
