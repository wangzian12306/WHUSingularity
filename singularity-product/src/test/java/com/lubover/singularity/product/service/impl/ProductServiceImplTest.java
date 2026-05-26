package com.lubover.singularity.product.service.impl;

import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.DetailCacheResult;
import com.lubover.singularity.product.cache.ProductCacheService.ListCacheResult;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.dto.UpdateProductRequest;
import com.lubover.singularity.product.entity.Product;
import com.lubover.singularity.product.event.ProductEventPublisher;
import com.lubover.singularity.product.exception.BusinessException;
import com.lubover.singularity.product.exception.ErrorCode;
import com.lubover.singularity.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    @Test
    void getByProductId_shouldWaitForCacheFillWithoutHittingDb() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        ProductView cachedView = buildView("p-1");
        when(cacheService.getDetail(anyString(), eq("p-1")))
                .thenReturn(DetailCacheResult.miss())
                .thenReturn(DetailCacheResult.value(cachedView));
        when(cacheService.tryLockDetail(anyString(), eq("p-1"), anyString())).thenReturn(false);

        ProductView result = service.getByProductId("p-1");

        assertEquals("p-1", result.getProductId());
        verify(productMapper, never()).selectByProductId(anyString());
    }

    @Test
    void getByProductId_shouldLoadFromDbWithLock() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        Product product = new Product();
        product.setProductId("p-2");
        product.setName("name");
        product.setCategory("cat");
        product.setPrice(BigDecimal.TEN);

        when(cacheService.getDetail(anyString(), eq("p-2")))
                .thenReturn(DetailCacheResult.miss())
                .thenReturn(DetailCacheResult.miss());
        when(cacheService.tryLockDetail(anyString(), eq("p-2"), anyString())).thenReturn(true);
        when(productMapper.selectByProductId("p-2")).thenReturn(product);

        ProductView result = service.getByProductId("p-2");

        assertEquals("p-2", result.getProductId());
        verify(productMapper).selectByProductId("p-2");
        ArgumentCaptor<String> prefixCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheService).putDetail(prefixCaptor.capture(), eq("p-2"), any(ProductView.class));
        assertTrue(prefixCaptor.getValue().startsWith("product:s"));
        verify(cacheService).getDetail(eq(prefixCaptor.getValue()), eq("p-2"));
        verify(cacheService).tryLockDetail(eq(prefixCaptor.getValue()), eq("p-2"), anyString());
        verify(cacheService).unlockDetail(eq(prefixCaptor.getValue()), eq("p-2"), anyString());
    }

    @Test
    void list_shouldWaitForCacheFillWithoutHittingDb() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        PageResponse<ProductView> cachedPage = PageResponse.of(List.of(buildView("p-1")), 1, 1, 10);
        when(cacheService.getList(anyString(), anyString()))
                .thenReturn(ListCacheResult.miss())
                .thenReturn(ListCacheResult.value(cachedPage));
        when(cacheService.tryLockList(anyString(), anyString(), anyString())).thenReturn(false);

        PageResponse<ProductView> result = service.list(1, null, null, 1, 10);

        assertEquals(1, result.getTotal());
        verify(productMapper, never()).selectList(any(), any(), any(), anyInt(), anyInt());
        verify(productMapper, never()).countList(any(), any(), any());
    }

    @Test
    void list_shouldLoadFromDbWithLock() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        Product product = new Product();
        product.setProductId("p-3");
        product.setName("name");
        product.setCategory("cat");
        product.setPrice(BigDecimal.TEN);

        when(cacheService.getList(anyString(), anyString()))
                .thenReturn(ListCacheResult.miss())
                .thenReturn(ListCacheResult.miss());
        when(cacheService.tryLockList(anyString(), anyString(), anyString())).thenReturn(true);
        when(productMapper.selectList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(product));
        when(productMapper.countList(any(), any(), any())).thenReturn(1L);

        PageResponse<ProductView> result = service.list(1, null, null, 1, 10);

        assertEquals(1L, result.getTotal());
        assertNotNull(result.getRecords());
        verify(productMapper).selectList(any(), any(), any(), anyInt(), anyInt());
        verify(productMapper).countList(any(), any(), any());
        verify(cacheService).putList(anyString(), eq(ProductCacheService.buildListHash(1, null, null, 1, 10)), any());
        verify(cacheService).unlockList(anyString(), anyString(), anyString());
    }

    @Test
    void getByProductId_shouldDegradeInfrastructureFailureToInternalError() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        when(cacheService.getDetail(anyString(), eq("p-degrade"))).thenThrow(new RuntimeException("redis down"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getByProductId("p-degrade"));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        verify(productMapper, never()).selectByProductId(anyString());
    }

    @Test
    void list_shouldReturnEmptyPageWhenReadPathDegrades() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        when(cacheService.getList(anyString(), anyString())).thenThrow(new RuntimeException("redis down"));

        PageResponse<ProductView> result = service.list(1, null, null, 2, 10);

        assertEquals(0, result.getTotal());
        assertEquals(2, result.getPageNo());
        assertEquals(10, result.getPageSize());
        verify(productMapper, never()).selectList(any(), any(), any(), anyInt(), anyInt());
        verify(productMapper, never()).countList(any(), any(), any());
    }

    @Test
    void update_shouldEvictDetailAndListCaches() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        prepareCacheDefaults(cacheService);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        Product existing = buildProduct("p-4");
        Product updated = buildProduct("p-4");
        updated.setName("updated");
        when(productMapper.selectByProductId("p-4"))
                .thenReturn(existing)
                .thenReturn(updated);
        when(productMapper.updateByProductId(any(Product.class))).thenReturn(1);

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("updated");

        ProductView result = service.update("p-4", request);

        assertEquals("updated", result.getName());
        verify(cacheService).evictDetail("p-4");
        verify(cacheService).evictAllLists();
    }

    private ProductView buildView(String productId) {
        ProductView view = new ProductView();
        view.setProductId(productId);
        view.setName("name");
        view.setCategory("cat");
        view.setPrice(BigDecimal.TEN);
        view.setVersion(1L);
        return view;
    }

    private void prepareCacheDefaults(ProductCacheService cacheService) {
        when(cacheService.detailDirtyVersion(anyString())).thenReturn(null);
        when(cacheService.listDirtyVersion()).thenReturn(null);
    }

    private Product buildProduct(String productId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setName("name");
        product.setCategory("cat");
        product.setPrice(BigDecimal.TEN);
        product.setVersion(1L);
        return product;
    }
}
