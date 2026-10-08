package org.example.jotangnote.service;

import org.example.jotangnote.entity.Note;

import java.util.List;

/**
 * 笔记查询和缓存管理业务接口
 */
public interface NoteService {

    /**
     * 根据id查询笔记，优先读取缓存，未命中时查询数据库
     *
     * @param id 笔记id
     * @return 笔记详情，笔记不存在时返回null；归属权限由调用方校验
     */
    Note getNote(Long id);

    /**
     * 根据用户id查询笔记列表，按更新时间和id倒序排列
     *
     * @param authorId 笔记所属用户id
     * @return 该用户的笔记列表
     */
    List<Note> getNotesByAuthorId(Long authorId);

    /**
     * 数据库事务提交后清理笔记缓存和重建锁
     *
     * @param id 笔记id
     */
    void invalidateNoteCache(Long id);
}
