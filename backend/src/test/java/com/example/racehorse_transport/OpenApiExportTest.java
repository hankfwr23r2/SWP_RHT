package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Ghi hợp đồng API (OpenAPI do springdoc sinh) ra frontend/openapi/openapi.json để frontend sinh client (KE_HOACH_TRIEN_KHAI.md mục 2.3).
// Khóa sắp theo thứ tự chữ cái để file ổn định giữa các lần chạy, so được bằng git diff. Không sửa tay file này.
@PostgresIntegrationTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    private static final Path OUTPUT = Path.of("..", "frontend", "openapi", "openapi.json");

    @Autowired private MockMvc mockMvc;

    @Test
    @SuppressWarnings("unchecked")
    void exportsApiContractForFrontend() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ObjectMapper sorted = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Map<String, Object> contract = sorted.readValue(body, Map.class);
        assertThat((String) contract.get("openapi")).startsWith("3.");
        assertThat((Map<String, Object>) contract.get("paths")).containsKey("/auth/customer/login");

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sorted.writeValueAsString(contract) + "\n", StandardCharsets.UTF_8);
        assertThat(OUTPUT).exists();
    }
}
