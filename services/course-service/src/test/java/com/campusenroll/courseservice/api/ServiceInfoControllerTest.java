package com.campusenroll.courseservice.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ServiceInfoController.class)
class ServiceInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void TestServiceInfoEndpointReturnsPhaseMetadata() throws Exception {
        mockMvc.perform(get("/internal/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("course-service"))
                .andExpect(jsonPath("$.phase").value("1-skeleton"))
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
