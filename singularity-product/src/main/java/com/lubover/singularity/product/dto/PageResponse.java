package com.lubover.singularity.product.dto;

import java.util.List;

public class PageResponse<T> {

    private List<T> records;
    private long total;
    private int pageNo;
    private int pageSize;
    private long totalPages;

    public static <T> PageResponse<T> of(List<T> records, long total, int pageNo, int pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotalPages((total + pageSize - 1) / pageSize);
        return response;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(long totalPages) {
        this.totalPages = totalPages;
    }
}
