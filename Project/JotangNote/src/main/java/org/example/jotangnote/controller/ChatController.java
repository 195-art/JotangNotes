package org.example.jotangnote.controller;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.jotangnote.ai.AiClient;
import org.example.jotangnote.common.JwtUtil;
import org.example.jotangnote.common.Result;
import org.example.jotangnote.dto.ChatDTO;
import org.example.jotangnote.entity.Note;
import org.example.jotangnote.service.NoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI对话接口，支持查询当前用户的笔记和保存会话历史
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // 会话缓存key前缀，后面拼接用户id和会话id
    private static final String SESSION_KEY_PREFIX = "chat:session:";
    // 会话历史有效期，每次保存后重新计时
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    // 工具调用的最大轮数，避免连续调用工具无法结束
    private static final int MAX_TOOL_ROUNDS = 5;

    private final AiClient aiClient;
    private final NoteService noteService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    // 定义笔记查询工具，向AI提供工具名称和参数格式
    private static final List<Map<String, Object>> NOTE_TOOL = List.of(
            Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "get_note_by_id",
                            "description", "根据笔记 ID 查询笔记的完整内容，包括标题和正文",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "noteId", Map.of("type", "integer", "description", "笔记的 ID")
                                    ),
                                    "required", List.of("noteId")
                            )
                    )
            )
    );

    /**
     * 发送对话消息并处理AI工具调用
     *
     * @param dto 会话id和用户消息，会话id为空时创建新会话
     * @param token 登录令牌
     * @return 会话id和AI回复
     * @throws Exception AI请求或工具参数处理失败时抛出异常
     */
    @PostMapping
    public Result chat(@Valid @RequestBody ChatDTO dto, @RequestHeader("Authorization") String token) throws Exception {
        // 校验登录令牌，按当前用户隔离会话和笔记查询权限
        Long userId = jwtUtil.parseToken(token);

        // 未指定会话id时创建新会话
        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 读取历史对话，追加本次用户消息
        List<Map<String, Object>> history = loadHistory(userId, sessionId);
        history.add(Map.of("role", "user", "content", dto.getMessage()));

        String finalAnswer = null;
        try {
            // 多轮处理工具调用，将查询结果提供给AI继续生成回复
            for (int i = 0; i < MAX_TOOL_ROUNDS; i++) {
                Map<String, Object> reply = aiClient.chatCompletion(history, NOTE_TOOL);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) reply.get("tool_calls");

                if (toolCalls == null || toolCalls.isEmpty()) {
                    // 未请求调用工具时，保存AI的最终回复并结束循环
                    finalAnswer = (String) reply.get("content");
                    history.add(Map.of("role", "assistant", "content", finalAnswer));
                    break;
                }

                // 保存包含工具调用信息的AI消息
                history.add(reply);

                // 执行每个工具调用
                for (Map<String, Object> call : toolCalls) {
                    String callId = (String) call.get("id");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) call.get("function");
                    String name = (String) fn.get("name");
                    String argsJson = (String) fn.get("arguments");

                    String toolResult;
                    if ("get_note_by_id".equals(name)) {
                        Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
                        Long noteId = ((Number) args.get("noteId")).longValue();
                        Note note = noteService.getNote(noteId);
                        // 校验笔记归属，他人的笔记统一按不存在处理
                        if (note != null && !note.getAuthorId().equals(userId)) {
                            note = null;
                        }
                        toolResult = note == null
                                ? "笔记不存在：id=" + noteId
                                : "标题：" + note.getTitle() + "\n内容：" + note.getContent();
                    } else {
                        toolResult = "未知工具：" + name;
                    }

                    // 保存工具执行结果，通过调用id与AI请求关联
                    history.add(Map.of(
                            "role", "tool",
                            "tool_call_id", callId,
                            "content", toolResult
                    ));
                }
                // 下一轮请求携带工具结果，供AI生成回复或继续调用工具
            }
        } finally {
            // 保存本轮会话历史，AI调用异常时也保留已生成的上下文
            saveHistory(userId, sessionId, history);
        }

        // 达到工具调用上限仍未得到回复时，返回提示信息
        if (finalAnswer == null) {
            finalAnswer = "抱歉，我连续调用了 " + MAX_TOOL_ROUNDS + " 次工具仍未得出结论，请换个问法或稍后再试。";
        }

        return Result.success(Map.of("sessionId", sessionId, "reply", finalAnswer));
    }

    /**
     * 生成用户隔离的会话缓存key
     *
     * @param userId 用户id
     * @param sessionId 会话id
     * @return 会话缓存key
     */
    private String sessionKey(Long userId, String sessionId) {
        return SESSION_KEY_PREFIX + userId + ":" + sessionId;
    }

    /**
     * 读取会话历史
     *
     * @param userId 用户id
     * @param sessionId 会话id
     * @return 会话消息列表，缓存不存在或读取失败时返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadHistory(Long userId, String sessionId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(sessionKey(userId, sessionId));
        } catch (Exception e) {
            // 缓存读取失败时按新会话处理
            log.warn("读取会话历史失败，按新会话处理：sessionId={}", sessionId, e);
            return new ArrayList<>();
        }
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>((List<Map<String, Object>>) objectMapper.readValue(json, List.class));
        } catch (Exception e) {
            log.warn("会话历史反序列化失败，按新会话处理：sessionId={}", sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存会话历史并更新有效期
     *
     * @param userId 用户id
     * @param sessionId 会话id
     * @param history 会话消息列表
     */
    private void saveHistory(Long userId, String sessionId, List<Map<String, Object>> history) {
        try {
            redisTemplate.opsForValue().set(
                    sessionKey(userId, sessionId),
                    objectMapper.writeValueAsString(history),
                    SESSION_TTL);
        } catch (Exception e) {
            log.error("会话历史写入 Redis 失败：sessionId={}", sessionId, e);
        }
    }
}
