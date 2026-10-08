package org.example.jotangnote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增和修改笔记的请求参数
 */
@Data
public class NoteDTO {
    // 笔记标题，不超过255个字符
    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过255")
    private String title;

    // 笔记正文
    @NotBlank(message = "内容不能为空")
    private String content;
}
