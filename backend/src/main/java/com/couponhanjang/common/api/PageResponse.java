package com.couponhanjang.common.api;

import com.couponhanjang.common.exception.ApiException;
import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> items, PageMeta page) {
    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(source.getContent(), new PageMeta(source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages()));
    }

    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid pagination");
        }
    }
}
