package com.techdevhub.mapper;

import com.techdevhub.entity.ChatConversation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatConversationMapper {

    /** INSERT IGNORE：已存在时不覆盖（标题归属首次生成的那一轮，防并发轮次相互改写） */
    @Insert("""
            insert ignore into chat_conversation (conversation_id, user_id, title)
            values (#{conversationId}, #{userId}, #{title})
            """)
    int insertIgnore(@Param("conversationId") String conversationId,
                     @Param("userId") Long userId,
                     @Param("title") String title);

    /** 仅在标题为空时补写（幂等：Python 端 title_done 标志 + 此处 IFNULL 双保险） */
    @Update("""
            update chat_conversation
            set title = ifnull(title, #{title}), update_time = now()
            where conversation_id = #{conversationId}
            """)
    int updateTitleIfNull(@Param("conversationId") String conversationId,
                          @Param("title") String title);

    /** 每轮 transcript 写入后刷新活跃时间（会话列表排序键） */
    @Update("update chat_conversation set update_time = now() where conversation_id = #{conversationId}")
    int touch(@Param("conversationId") String conversationId);

    /**
     * 用户的会话列表：注册表为主表（标题），LEFT JOIN 消息表取每个会话
     * 最后一条消息作预览。注册表缺失的存量会话不出现在列表中
     * （迁移脚本已补登记，正常不会发生）。
     */
    @Select("""
            select c.conversation_id as conversationId,
                   c.title           as title,
                   t.role            as lastRole,
                   t.content         as lastMessage,
                   t.create_time     as lastTime
            from chat_conversation c
            left join chat_transcript t
                   on t.id = (select max(id) from chat_transcript
                              where conversation_id = c.conversation_id)
            where c.user_id = #{userId}
            order by c.update_time desc
            limit #{limit}
            """)
    List<Map<String, Object>> selectConversationsByUser(@Param("userId") Long userId,
                                                        @Param("limit") int limit);

    /** 删除会话（注册表），仅限归属人；返回删除行数（0=会话不存在或非本人） */
    @Delete("""
            delete from chat_conversation
            where conversation_id = #{conversationId} and user_id = #{userId}
            """)
    int deleteConversation(@Param("conversationId") String conversationId,
                           @Param("userId") Long userId);
}
