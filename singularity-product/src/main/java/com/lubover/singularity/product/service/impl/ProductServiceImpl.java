package com.lubover.singularity.product.service.impl;

import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.PipelineExecutor;
import com.lubover.singularity.pipeline.impl.DefaultPipelineExecutor;
import com.lubover.singularity.pipeline.interceptor.CacheStampedeGuardInterceptor;
import com.lubover.singularity.pipeline.interceptor.MetricsTraceInterceptor;
import com.lubover.singularity.pipeline.interceptor.ReadThroughCacheInterceptor;
import com.lubover.singularity.pipeline.read.CacheNullHitHandler;
import com.lubover.singularity.pipeline.read.ReadCache;
import com.lubover.singularity.pipeline.read.ReadLockManager;
import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.dto.ApiResponse;
import com.lubover.singularity.product.dto.CreateProductRequest;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductDetailView;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.dto.StockView;
import com.lubover.singularity.product.dto.UpdateProductRequest;
import com.lubover.singularity.product.entity.Product;
import com.lubover.singularity.product.event.ProductEventPublisher;
import com.lubover.singularity.product.event.ProductUpdatedEvent;
import com.lubover.singularity.product.exception.BusinessException;
import com.lubover.singularity.product.exception.ErrorCode;
import com.lubover.singularity.product.feign.StockClient;
import com.lubover.singularity.product.mapper.ProductMapper;
import com.lubover.singularity.product.observability.ProductObservabilityService;
import com.lubover.singularity.product.read.ProductDetailReadCache;
import com.lubover.singularity.product.read.ProductListReadCache;
import com.lubover.singularity.product.read.ProductReadMeta;
import com.lubover.singularity.product.read.ProductReadOperations;
import com.lubover.singularity.product.read.ProductReadLockManager;
import com.lubover.singularity.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final int STATUS_OFFLINE = 0;
    private static final int STATUS_ONLINE = 1;
    private static final int CACHE_LOCK_MAX_WAIT_ATTEMPTS = 100;
    private static final long CACHE_LOCK_WAIT_MS = 50L;

    private final ProductMapper productMapper;
    private final ProductCacheService cacheService;
    private final ProductEventPublisher eventPublisher;
    private final PipelineExecutor pipelineExecutor;
    private final StockClient stockClient;
    private final ProductObservabilityService observabilityService;

    public ProductServiceImpl(
            ProductMapper productMapper,
            ProductCacheService cacheService,
            ProductEventPublisher eventPublisher) {
        this(
                productMapper,
                cacheService,
                eventPublisher,
                new DefaultPipelineExecutor(),
                null,
                new ProductObservabilityService());
    }

    @Autowired
    public ProductServiceImpl(
            ProductMapper productMapper,
            ProductCacheService cacheService,
            ProductEventPublisher eventPublisher,
            PipelineExecutor pipelineExecutor,
            StockClient stockClient,
            ProductObservabilityService observabilityService) {
        this.productMapper = productMapper;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.pipelineExecutor = pipelineExecutor;
        this.stockClient = stockClient;
        this.observabilityService = observabilityService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductView create(CreateProductRequest request) {
        validateCreateRequest(request);
        Product product = new Product();
        product.setProductId(request.getProductId().trim());
        product.setName(request.getName().trim());
        product.setSubtitle(trimToNull(request.getSubtitle()));
        product.setMainImage(trimToNull(request.getMainImage()));
        product.setCategory(request.getCategory().trim());
        product.setTags(trimToNull(request.getTags()));
        product.setStatus(request.getStatus() == null ? STATUS_ONLINE : request.getStatus());
        product.setPrice(request.getPrice());
        product.setVersion(0L);
        product.setIsDeleted(0);

        try {
            int affected = productMapper.insert(product);
            if (affected <= 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "create product failed");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_EXISTS, "productId already exists");
        }

        ProductView view = ProductView.from(productMapper.selectByProductId(product.getProductId()));
        cacheService.putDetail(view.getProductId(), view);
        cacheService.evictAllLists();
        eventPublisher.publishAfterCommit(new ProductUpdatedEvent(view.getProductId(), ProductUpdatedEvent.Action.CREATED));
        return view;
    }

    @Override
    public ProductView getByProductId(String productId) {
        validateProductId(productId);
        String key = productId.trim();

        Operation operation = ProductReadOperations.detail(key);
        ReadCache<ProductView> readCache = new ProductDetailReadCache(cacheService);
        ReadLockManager lockManager = new ProductReadLockManager(cacheService);
        ExecutionResult<ProductView> result = pipelineExecutor.execute(
                operation,
                List.of(
                        new MetricsTraceInterceptor<>(),
                        new ReadThroughCacheInterceptor<>(readCache, detailNullHitHandler()),
                        new CacheStampedeGuardInterceptor<>(
                                lockManager,
                                readCache,
                                detailNullHitHandler(),
                                CACHE_LOCK_MAX_WAIT_ATTEMPTS,
                                CACHE_LOCK_WAIT_MS)),
                context -> {
                    log.info("product detail cache miss, querying db with pipeline: productId={}", key);
                    Product product = productMapper.selectByProductId(key);
                    if (product == null) {
                        cacheService.putDetail(key, null);
                        log.info("product detail db miss, cached null marker: productId={}", key);
                        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                    }
                    context.setResult(ExecutionResult.success(ProductView.from(product)));
                });
        ensureReadSuccess(result);
        observabilityService.recordProductRead(String.valueOf(result.getMeta().get(ProductReadMeta.SOURCE)));
        return result.getData();
    }

    @Override
    public ProductDetailView getDetailWithStock(String productId) {
        ProductView product = getByProductId(productId);
        StockView stock = fetchStockView(product.getProductId());
        return new ProductDetailView(product, stock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductView update(String productId, UpdateProductRequest request) {
        validateProductId(productId);
        validateUpdateRequest(request);
        Product exists = productMapper.selectByProductId(productId.trim());
        if (exists == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Product update = new Product();
        update.setProductId(productId.trim());
        update.setName(trimToNull(request.getName()));
        update.setSubtitle(trimToNull(request.getSubtitle()));
        update.setMainImage(trimToNull(request.getMainImage()));
        update.setCategory(trimToNull(request.getCategory()));
        update.setTags(trimToNull(request.getTags()));
        update.setStatus(request.getStatus());
        update.setPrice(request.getPrice());

        int affected = productMapper.updateByProductId(update);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "update product failed");
        }

        ProductView view = ProductView.from(productMapper.selectByProductId(productId.trim()));
        evictAfterWrite(productId.trim());
        eventPublisher.publishAfterCommit(new ProductUpdatedEvent(productId.trim(), ProductUpdatedEvent.Action.UPDATED));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductView updateStatus(String productId, Integer status) {
        validateProductId(productId);
        validateStatus(status);
        if (status == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "status is required");
        }

        int affected = productMapper.updateStatusByProductId(productId.trim(), status);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ProductView view = ProductView.from(productMapper.selectByProductId(productId.trim()));
        evictAfterWrite(productId.trim());
        eventPublisher.publishAfterCommit(new ProductUpdatedEvent(productId.trim(), ProductUpdatedEvent.Action.UPDATED));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String productId) {
        validateProductId(productId);
        int affected = productMapper.markDeleted(productId.trim());
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        evictAfterWrite(productId.trim());
        eventPublisher.publishAfterCommit(new ProductUpdatedEvent(productId.trim(), ProductUpdatedEvent.Action.DELETED));
    }

    @Override
    public PageResponse<ProductView> list(Integer status, String category, String keyword, int pageNo, int pageSize) {
        validateListParam(status, pageNo, pageSize);

        int normalizedPageNo = Math.max(pageNo, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedCategory = trimToNull(category);
        String normalizedKeyword = trimToNull(keyword);

        String queryHash = ProductCacheService.buildListHash(
                status, normalizedCategory, normalizedKeyword, normalizedPageNo, normalizedPageSize);

        Operation operation = ProductReadOperations.list(
                queryHash,
                status,
                normalizedCategory,
                normalizedKeyword,
                normalizedPageNo,
                normalizedPageSize);
        ExecutionResult<PageResponse<ProductView>> result = pipelineExecutor.execute(
                operation,
                List.of(
                        new MetricsTraceInterceptor<>(),
                        new ReadThroughCacheInterceptor<>(new ProductListReadCache(cacheService), listNullHitHandler()),
                        new CacheStampedeGuardInterceptor<>(
                                new ProductReadLockManager(cacheService),
                                new ProductListReadCache(cacheService),
                                listNullHitHandler(),
                                CACHE_LOCK_MAX_WAIT_ATTEMPTS,
                                CACHE_LOCK_WAIT_MS)),
                context -> {
                    log.info("product list cache miss, querying db with pipeline: hash={}", queryHash);
                    int offset = (normalizedPageNo - 1) * normalizedPageSize;
                    List<ProductView> views = productMapper
                            .selectList(status, normalizedCategory, normalizedKeyword, offset, normalizedPageSize)
                            .stream()
                            .map(ProductView::from)
                            .collect(Collectors.toList());
                    long total = productMapper.countList(status, normalizedCategory, normalizedKeyword);
                    context.setResult(ExecutionResult.success(
                            PageResponse.of(views, total, normalizedPageNo, normalizedPageSize)));
                });
        ensureReadSuccess(result);
        observabilityService.recordProductRead(String.valueOf(result.getMeta().get(ProductReadMeta.SOURCE)));
        return result.getData();
    }

    private CacheNullHitHandler<ProductView> detailNullHitHandler() {
        return context -> {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        };
    }

    private CacheNullHitHandler<PageResponse<ProductView>> listNullHitHandler() {
        return context -> context.setResult(ExecutionResult.success(emptyPage(context.getOperation())));
    }

    private PageResponse<ProductView> emptyPage(Operation operation) {
        int pageNo = (Integer) operation.getMetadata().get(ProductReadOperations.META_PAGE_NO);
        int pageSize = (Integer) operation.getMetadata().get(ProductReadOperations.META_PAGE_SIZE);
        return PageResponse.of(List.of(), 0, pageNo, pageSize);
    }

    private void ensureReadSuccess(ExecutionResult<?> result) {
        if (result == null || result.isSuccess()) {
            return;
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, result.getMessage());
    }

    private StockView fetchStockView(String productId) {
        if (stockClient == null) {
            observabilityService.recordStockQuery(false);
            return null;
        }

        try {
            ApiResponse<StockView> response = stockClient.getStock(productId);
            if (response != null && response.isSuccess()) {
                observabilityService.recordStockQuery(true);
                return response.getData();
            }
            observabilityService.recordStockQuery(false);
            log.warn("stock view unavailable: productId={} message={}", productId,
                    response == null ? "null response" : response.getMessage());
            return null;
        } catch (Exception e) {
            observabilityService.recordStockQuery(false);
            log.warn("stock view query failed: productId={} err={}", productId, e.getMessage());
            return null;
        }
    }

    private void evictAfterWrite(String productId) {
        cacheService.evictDetail(productId);
        cacheService.evictAllLists();
        log.info("product cache evicted after write: productId={}", productId);
    }

    private void validateCreateRequest(CreateProductRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM);
        }
        validateProductId(request.getProductId());
        validateName(request.getName());
        validateCategory(request.getCategory());
        validateStatus(request.getStatus());
        validatePrice(request.getPrice());
    }

    private void validateUpdateRequest(UpdateProductRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM);
        }
        if (request.getName() == null
                && request.getSubtitle() == null
                && request.getMainImage() == null
                && request.getCategory() == null
                && request.getTags() == null
                && request.getStatus() == null
                && request.getPrice() == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "at least one field must be provided");
        }
        if (request.getName() != null) {
            validateName(request.getName());
        }
        if (request.getCategory() != null) {
            validateCategory(request.getCategory());
        }
        validateStatus(request.getStatus());
        if (request.getPrice() != null) {
            validatePrice(request.getPrice());
        }
    }

    private void validateListParam(Integer status, int pageNo, int pageSize) {
        validateStatus(status);
        if (pageNo <= 0) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "pageNo must be positive");
        }
        if (pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "pageSize must be between 1 and 100");
        }
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.trim().isEmpty() || productId.trim().length() > 64) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "productId is invalid");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "name is invalid");
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.trim().isEmpty() || category.trim().length() > 64) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "category is invalid");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_OFFLINE && status != STATUS_ONLINE) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "status is invalid");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "price is invalid");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
