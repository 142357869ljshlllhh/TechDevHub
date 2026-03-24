package com.techdevhub.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long total;
    private Long pageNum;
    private Long pageSize;
    private List<T> records;

    public static <T> PageResult<T> of(Long total, Long pageNum, Long pageSize, List<T> records) {
        return new PageResult<>(total, pageNum, pageSize, records);
    }

    public static <T> PageResult<T> empty(Long pageNum, Long pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, Collections.emptyList());
    }
}
