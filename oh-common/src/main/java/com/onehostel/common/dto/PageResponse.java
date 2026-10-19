package com.onehostel.dto.helper;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    public static <T> PageResponse<T> unpaged(List<T> content) {
        return new PageResponse<>(content, 0, content.size(), content.size(), content.isEmpty() ? 0 : 1);
    }
}
