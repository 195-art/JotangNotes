package org.example.jotangnote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.jotangnote.entity.Note;

/**
 * 笔记数据访问接口
 */
public interface NoteMapper extends BaseMapper<Note> {
    /**
     * 根据id查询笔记版本号，保留用于兼容已有版本字段
     *
     * @param id 笔记id
     * @return 笔记版本号，笔记不存在时返回null
     */
    @Select("SELECT cache_version FROM notes WHERE id = #{id}")
    Long selectCacheVersion(@Param("id") Long id);

    /**
     * 根据笔记id和用户id修改笔记，同时更新修改时间和版本号
     *
     * @param id 笔记id
     * @param userId 笔记所属用户id
     * @param title 修改后的标题
     * @param content 修改后的正文
     * @return 受影响的记录数，笔记不存在或不属于该用户时为0
     */
    @Update("UPDATE notes SET title = #{title}, content = #{content}, "
            + "updated_at = CURRENT_TIMESTAMP, cache_version = cache_version + 1 "
            + "WHERE id = #{id} AND author_id = #{userId}")
    int updateOwnedNote(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("title") String title, @Param("content") String content);
}
