package edu.fpt.sba.scms.controller;

import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SyncConfigFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(400);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    private String cooToken;
    private Integer targetBranchId;

    @BeforeEach
    void setUp() throws Exception {
        String loginPayload = """
                {
                    "username": "coo",
                    "password": "Admin@123"
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        this.cooToken = com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.data.accessToken");

        int seq = SEQ.incrementAndGet();
        Branch branch = Branch.builder()
                .code("B_SY" + seq)
                .name("Chi nhánh Test Sync " + seq)
                .address("101 Sync Street")
                .openingTime(LocalTime.of(7, 0))
                .closingTime(LocalTime.of(22, 0))
                .status(BranchStatus.OPEN)
                .build();
        Branch saved = branchRepository.save(branch);
        this.targetBranchId = saved.getId();
    }

    @Test
    @DisplayName("UC14: COO phát lệnh đồng bộ cấu hình chuỗi xuống chi nhánh mục tiêu")
    void testSyncConfigFlow() throws Exception {
        String syncPayload = """
                {
                    "syncCategories": true,
                    "syncProducts": true,
                    "syncVouchers": true,
                    "targetBranchIds": [%d]
                }
                """.formatted(targetBranchId);

        mockMvc.perform(post("/api/v1/chain-sync")
                        .header("Authorization", "Bearer " + cooToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.syncedBranchIds", hasSize(1)))
                .andExpect(jsonPath("$.data.syncedBranchIds[0]").value(targetBranchId));
    }
}
