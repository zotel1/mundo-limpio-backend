package com.mundolimpio.application.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundolimpio.application.product.dto.ProductRequest;
import com.mundolimpio.application.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductControllerPatchIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;


    @BeforeEach
    public void setup() { productRepository.deleteAll(); }

    @Test
    void shouldReactivateProductSuccessfully() throws Exception {
        ProductRequest request = new ProductRequest("REACTIV-001", "Para reactivar", new BigDecimal("10.00"));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        Long productId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/v1/products/" + productId))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/products/" + productId + "/reactivate"))
                .andExpect(status().isNoContent());

        var productFromDb = productRepository.findById(productId);
        assertTrue(productFromDb.isPresent());
        assertTrue(productFromDb.get().getActive());
    }

    @Test
    void shouldIncludeReactivatedProductInActivateList() throws Exception {
        ProductRequest request = new ProductRequest("BACK-TO-ACTIVE", "Vuelve a ser activo", new BigDecimal("10.00"));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        Long productId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/v1/products/" + productId))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/products/" + productId + "/reactivate"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
