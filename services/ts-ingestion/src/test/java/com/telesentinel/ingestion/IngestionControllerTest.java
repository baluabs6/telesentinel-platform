package com.telesentinel.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telesentinel.ingestion.controller.IngestionController;
import com.telesentinel.ingestion.service.IngestionService;
import com.telesentinel.ingestion.service.IngestionService.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean IngestionService service;

    private static final String ALARM = """
        {"alarmId":"A-1","nodeId":"gnb-1","severity":"CRITICAL","type":"LINK_DOWN",
         "message":"down","raisedAt":"2026-10-04T10:15:30Z"}""";

    @Test
    void acceptsValidAlarm() throws Exception {
        when(service.ingestAlarm(any())).thenReturn(Result.ACCEPTED);
        mvc.perform(post("/api/v1/alarms").contentType(MediaType.APPLICATION_JSON).content(ALARM))
           .andExpect(status().isAccepted());
    }

    @Test
    void duplicateAlarmReturns200() throws Exception {
        when(service.ingestAlarm(any())).thenReturn(Result.DUPLICATE);
        mvc.perform(post("/api/v1/alarms").contentType(MediaType.APPLICATION_JSON).content(ALARM))
           .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidCdr() throws Exception {
        String bad = "{\"cdrId\":\"C-1\",\"callingNumber\":\"abc\",\"calledNumber\":\"+123456\","
                   + "\"startTime\":\"2026-10-04T10:16:00Z\",\"durationSeconds\":4,\"callType\":\"LOCAL\"}";
        mvc.perform(post("/api/v1/cdrs").contentType(MediaType.APPLICATION_JSON).content(bad))
           .andExpect(status().isBadRequest());
    }
}
