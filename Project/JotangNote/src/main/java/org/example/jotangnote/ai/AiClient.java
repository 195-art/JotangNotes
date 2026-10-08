package org.example.jotangnote.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * AI服务客户端，封装对话请求和工具调用响应
 */
@Component
public class AiClient {

    private final RestClient restClient;
    private final String model;

    /**
     * 初始化AI请求客户端
     *
     * @param baseUrl AI服务地址
     * @param apiKey AI服务访问密钥
     * @param model 使用的模型名称
     */
    public AiClient(@Value("${mimo.base-url}") String baseUrl,
                    @Value("${mimo.api-key}") String apiKey,
                    @Value("${mimo.model:mimo-v2-flash}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 发送对话请求，获取AI回复或工具调用信息
     *
     * @param messages 历史对话和当前消息
     * @param tools 可用工具定义，为null时不传递工具参数
     * @return 首个候选结果中的AI消息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        // 根据是否提供工具定义封装请求参数
        Map<String, Object> requestBody = tools == null
                ? Map.of("model", model, "messages", messages)
                : Map.of("model", model, "messages", messages, "tools", tools);

        // 调用AI对话接口
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        // 校验响应内容，避免返回空结果或无法识别的响应
        if (response == null) {
            throw new RuntimeException("AI 服务无响应");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("AI 响应格式异常：" + response);
        }

        // 返回AI消息，具体工具执行由对话接口处理
        return (Map<String, Object>) choices.get(0).get("message");
    }
}
