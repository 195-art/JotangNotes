package org.example.jotangnote.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.jotangnote.common.JwtUtil;
import org.example.jotangnote.common.Result;
import org.example.jotangnote.config.RabbitConfig;
import org.example.jotangnote.dto.NoteDTO;
import org.example.jotangnote.dto.NoteOperationMessage;
import org.example.jotangnote.entity.Note;
import org.example.jotangnote.service.NoteService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 私人笔记管理接口
 */
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {
    private final NoteService noteService;
    private final JwtUtil jwtUtil;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 查询当前用户的笔记列表
     *
     * @param token 登录令牌
     * @return 当前用户的笔记列表
     */
    @GetMapping("/my")
    public Result getMyNotes(@RequestHeader("Authorization") String token) {
        // 从登录令牌中获取当前用户id
        Long userId = jwtUtil.parseToken(token);
        return Result.success(noteService.getNotesByAuthorId(userId));
    }

    /**
     * 提交新增笔记请求
     *
     * @param dto 笔记标题和正文
     * @param token 登录令牌
     * @return 请求受理结果，笔记由消息消费者异步写入数据库
     */
    @PostMapping
    public Result addNote(@Valid @RequestBody NoteDTO dto, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.parseToken(token);
        // 封装新增消息，使用唯一消息id识别重复投递
        var msg = new NoteOperationMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setOperationType("CREATE");
        msg.setTitle(dto.getTitle());
        msg.setContent(dto.getContent());
        msg.setUserId(userId);
        // 发送到笔记保序队列，由消费者完成新增操作
        rabbitTemplate.convertAndSend(RabbitConfig.NOTE_EXCHANGE, RabbitConfig.NOTE_ROUTING_KEY, msg);
        return Result.success("请求已受理，正在处理");
    }

    /**
     * 根据id查询当前用户的笔记详情
     *
     * @param id 笔记id
     * @param token 登录令牌
     * @return 笔记详情，或笔记不存在、无访问权限的提示
     */
    @GetMapping("/{id}")
    public Result getNote(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.parseToken(token);
        Note note = noteService.getNote(id);
        if (note == null) {
            return Result.fail("笔记不存在");
        }
        // 校验笔记归属，用户只能查看自己的笔记
        if (!note.getAuthorId().equals(userId)) {
            return Result.fail("无权限查看他人笔记");
        }
        return Result.success(note);
    }

    /**
     * 提交修改笔记请求
     *
     * @param id 笔记id
     * @param dto 修改后的标题和正文
     * @param token 登录令牌
     * @return 请求受理结果，或笔记不存在、无修改权限的提示
     */
    @PutMapping("/{id}")
    public Result updateNote(@PathVariable Long id,
                             @Valid @RequestBody NoteDTO dto,
                             @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.parseToken(token);
        // 查询笔记并校验归属，通过后才能提交修改请求
        Note note = noteService.getNote(id);
        if (note == null || !note.getAuthorId().equals(userId)) {
            return Result.fail(note == null ? "笔记不存在" : "无权限修改他人笔记");
        }
        // 封装修改消息，消费者写入数据库后清理笔记缓存
        var msg = new NoteOperationMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setOperationType("UPDATE");
        msg.setNoteId(id);
        msg.setTitle(dto.getTitle());
        msg.setContent(dto.getContent());
        msg.setUserId(userId);
        rabbitTemplate.convertAndSend(RabbitConfig.NOTE_EXCHANGE, RabbitConfig.NOTE_ROUTING_KEY, msg);
        return Result.success("请求已受理，正在处理");
    }

    /**
     * 提交删除笔记请求
     *
     * @param id 笔记id
     * @param token 登录令牌
     * @return 请求受理结果，或笔记不存在、无删除权限的提示
     */
    @DeleteMapping("/{id}")
    public Result deleteNote(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.parseToken(token);
        // 查询笔记并校验归属，通过后才能提交删除请求
        Note note = noteService.getNote(id);
        if (note == null || !note.getAuthorId().equals(userId)) {
            return Result.fail(note == null ? "笔记不存在" : "无权限删除他人笔记");
        }
        // 封装删除消息，与新增、修改操作共用保序队列
        var msg = new NoteOperationMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setOperationType("DELETE");
        msg.setNoteId(id);
        msg.setUserId(userId);
        rabbitTemplate.convertAndSend(RabbitConfig.NOTE_EXCHANGE, RabbitConfig.NOTE_ROUTING_KEY, msg);
        return Result.success("请求已受理，正在处理");
    }
}
