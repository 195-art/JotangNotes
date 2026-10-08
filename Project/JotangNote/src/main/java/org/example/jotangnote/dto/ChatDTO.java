package org.example.jotangnote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI对话请求参数
 */
@Data
public class ChatDTO {
    // 会话id，为空时由对话接口创建新会话
    private String sessionId;

    // 本次发送给AI的消息内容
    @NotBlank(message = "消息内容不能为空")
    private String message;
}
