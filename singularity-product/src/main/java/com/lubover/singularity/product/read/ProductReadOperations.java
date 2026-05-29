package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.DefaultOperation;
import com.lubover.singularity.pipeline.Operation;

import java.util.HashMap;
import java.util.Map;

public final class ProductReadOperations {

    public static final String META_PRODUCT_ID = "productId";
    public static final String META_QUERY_HASH = "queryHash";
    public static final String META_STATUS = "status";
    public static final String META_CATEGORY = "category";
    public static final String META_KEYWORD = "keyword";
    public static final String META_PAGE_NO = "pageNo";
    public static final String META_PAGE_SIZE = "pageSize";

    private ProductReadOperations() {
    }

    public static Operation detail(String productId) {
        return DefaultOperation.read(
                "product:detail:" + productId,
                Map.of(META_PRODUCT_ID, productId));
    }

    public static Operation list(
            String queryHash,
            Integer status,
            String category,
            String keyword,
            int pageNo,
            int pageSize) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_QUERY_HASH, queryHash);
        metadata.put(META_STATUS, status);
        metadata.put(META_CATEGORY, category);
        metadata.put(META_KEYWORD, keyword);
        metadata.put(META_PAGE_NO, pageNo);
        metadata.put(META_PAGE_SIZE, pageSize);
        return DefaultOperation.read("product:list:" + queryHash, metadata);
    }
}
