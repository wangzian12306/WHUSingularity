package com.lubover.singularity.stock.controller;

import com.lubover.singularity.stock.dto.SlotPreheatResponse;
import com.lubover.singularity.stock.service.SlotWarmupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SlotWarmupControllerContractTest {

    private MockMvc mockMvc;

    @Mock
    private SlotWarmupService slotWarmupService;

    @InjectMocks
    private SlotWarmupController slotWarmupController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(slotWarmupController)
                .build();
    }

    @Test
    void preheatShouldReturn200WhenOverwriteFalse() throws Exception {
        when(slotWarmupService.warmupSlot(anyString(), isNull(), anyLong(), anyBoolean()))
                .thenReturn(new SlotPreheatResponse("stock:A", true, "1000", "slot预热成功"));

        mockMvc.perform(post("/api/stock/slots/preheat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "slotId": "A",
                      "quantity": 1000,
                      "overwrite": false
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redisKey").value("stock:A"))
                .andExpect(jsonPath("$.written").value(true))
                .andExpect(jsonPath("$.currentValue").value("1000"));
    }

    @Test
    void preheatShouldReturn200WhenOverwriteTrue() throws Exception {
        when(slotWarmupService.warmupSlot(anyString(), anyString(), anyLong(), anyBoolean()))
                .thenReturn(new SlotPreheatResponse("stock:custom-bucket", true, "2000", "slot已覆盖预热"));

        mockMvc.perform(post("/api/stock/slots/preheat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "slotId": "A",
                      "redisKey": "stock:custom-bucket",
                      "quantity": 2000,
                      "overwrite": true
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redisKey").value("stock:custom-bucket"))
                .andExpect(jsonPath("$.written").value(true))
                .andExpect(jsonPath("$.currentValue").value("2000"));
    }

    @Test
    void preheatInvalidParamShouldReturn400() throws Exception {
        when(slotWarmupService.warmupSlot(anyString(), anyString(), anyLong(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("quantity必须大于0"));

        mockMvc.perform(post("/api/stock/slots/preheat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "slotId": "A",
                      "redisKey": "stock:custom-bucket",
                      "quantity": -1,
                      "overwrite": false
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("quantity必须大于0"));
    }
}
