package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageResponse<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;
    @JsonProperty("total_pages")
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> list, long total, int page, int size, int totalPages) {
        PageResponse<T> response = new PageResponse<>();
        response.setList(list);
        response.setTotal(total);
        response.setPage(page);
        response.setSize(size);
        response.setTotalPages(totalPages);
        return response;
    }
}
