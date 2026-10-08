package org.example.jotangnote;

import io.jsonwebtoken.JwtException;
import org.example.jotangnote.common.GlobalExceptionHandler;
import org.example.jotangnote.common.JwtUtil;
import org.example.jotangnote.controller.NoteController;
import org.example.jotangnote.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationTests {
    @Test
    void expiredAndMissingTokensReturn401() throws Exception {
        var jwt = mock(JwtUtil.class);
        when(jwt.parseToken("Bearer expired")).thenThrow(new JwtException("expired"));
        var mvc = MockMvcBuilders.standaloneSetup(new NoteController(mock(NoteService.class), jwt, mock(RabbitTemplate.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/notes/my").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/notes/my"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
}
