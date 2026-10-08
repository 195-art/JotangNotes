package org.example.jotangnote.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记操作消息，通过RabbitMQ传递给笔记消费者
 */
@Data
public class NoteOperationMessage implements Serializable {
    // 唯一消息id，用于数据库幂等校验
    private String messageId;
    // 操作类型：CREATE、UPDATE或DELETE
    private String operationType;
    // 待修改或删除的笔记id，新增时无需填写
    private Long noteId;
    // 新增或修改后的笔记标题
    private String title;
    // 新增或修改后的笔记正文
    private String content;
    // 发起操作的用户id，用于记录归属和校验权限
    private Long userId;
}
