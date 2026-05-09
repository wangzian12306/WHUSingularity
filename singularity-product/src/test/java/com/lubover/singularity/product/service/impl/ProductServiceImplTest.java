package com.lubover.singularity.product.service.impl;

import com.lubover.singularity.product.cache.ProductCacheService;
import com.lubover.singularity.product.cache.ProductCacheService.DetailCacheResult;
import com.lubover.singularity.product.cache.ProductCacheService.ListCacheResult;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.entity.Product;
import com.lubover.singularity.product.event.ProductEventPublisher;
import com.lubover.singularity.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        ProductView cachedView = buildView("p-1");
        when(cacheService.getDetail("p-1"))
                .thenReturn(DetailCacheResult.miss())
                .thenReturn(DetailCacheResult.value(cachedView));
        when(cacheService.tryLockDetail(anyString(), anyString())).thenReturn(false);

        ProductView result = service.getByProductId("p-1");

        assertEquals("p-1", result.getProductId());
        verify(productMapper, never()).selectByProductId(anyString());
    }

    @Test
    void getByProductId_shouldLoadFromDbWithLock() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        Product product = new Product();
        product.setProductId("p-2");
        product.setName("name");
        product.setCategory("cat");
        product.setPrice(BigDecimal.TEN);

        when(cacheService.getDetail("p-2"))
                .thenReturn(DetailCacheResult.miss())
                .thenReturn(DetailCacheResult.miss());
        when(cacheService.tryLockDetail(anyString(), anyString())).thenReturn(true);
        when(productMapper.selectByProductId("p-2")).thenReturn(product);

        ProductView result = service.getByProductId("p-2");

        assertEquals("p-2", result.getProductId());
        verify(productMapper).selectByProductId("p-2");
        verify(cacheService).putDetail(eq("p-2"), any(ProductView.class));
        verify(cacheService).unlockDetail(anyString(), anyString());
    }

    @Test
    void list_shouldWaitForCacheFillWithoutHittingDb() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductCacheService cacheService = mock(ProductCacheService.class);
        ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        PageResponse<ProductView> cachedPage = PageResponse.of(List.of(buildView("p-1")), 1, 1, 10);
        when(cacheService.getList(anyString()))
                .thenReturn(ListCacheResult.miss())
                .thenReturn(ListCacheResult.value(cachedPage));
        when(cacheService.tryLockList(anyString(), anyString())).thenReturn(false);

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
        ProductServiceImpl service = new ProductServiceImpl(productMapper, cacheService, eventPublisher);

        Product product = new Product();
        product.setProductId("p-3");
        product.setName("name");
        product.setCategory("cat");
        product.setPrice(BigDecimal.TEN);

        when(cacheService.getList(anyString()))
                .thenReturn(ListCacheResult.miss())
                .thenReturn(ListCacheResult.miss());
        when(cacheService.tryLockList(anyString(), anyString())).thenReturn(true);
        when(productMapper.selectList(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(product));
        when(productMapper.countList(any(), any(), any())).thenReturn(1L);

        PageResponse<ProductView> result = service.list(1, null, null, 1, 10);

        assertEquals(1L, result.getTotal());
        assertNotNull(result.getRecords());
        verify(productMapper).selectList(any(), any(), any(), anyInt(), anyInt());
        verify(productMapper).countList(any(), any(), any());
        verify(cacheService).putList(eq(ProductCacheService.buildListHash(1, null, null, 1, 10)), any());
        verify(cacheService).unlockList(anyString(), anyString());
    }

    private ProductView buildView(String productId) {
        ProductView view = new ProductView();
        view.setProductId(productId);
        view.setName("name");
        view.setCategory("cat");
        view.setPrice(BigDecimal.TEN);
        return view;
    }
}
