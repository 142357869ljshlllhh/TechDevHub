package com.techdevhub.mapper;

import com.techdevhub.entity.ChatTranscript;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatTranscriptMapper {

    @Insert("""
            insert into chat_transcript (conversation_id, user_id, role, content)
            values (#{conversationId}, #{userId}, #{role}, #{content})
            """)
    int insert(ChatTranscript record);

    /**
     * 会话内按序取消息（归属校验内联在 WHERE 里：user_id 不符时返回空列表，
     * 不泄露会话存在性）。beforeId 为游标（分页加载更早消息），null 表示取最新一页。
     */
    @Select("""
            <script>
            select id, conversation_id, user_id, role, content, create_time
            from chat_transcript
            where conversation_id = #{conversationId}
              and user_id = #{userId}
              <if test="beforeId != null"> and id &lt; #{beforeId}</if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<ChatTranscript> selectByConversation(@Param("conversationId") String conversationId,
                                              @Param("userId") Long userId,
                                              @Param("beforeId") Long beforeId,
                                              @Param("limit") int limit);

    /**
     * 用户的会话列表：按 conversation_id 聚合，取每个会话最后一条消息作为预览。
     * 子查询先圈定每个会话的 MAX(id)，再回表取该行——避免全量排序。
     */
    @Select("""
            select t.conversation_id as conversationId,
                   t.role            as lastRole,
                   t.content         as lastMessage,
                   t.create_time     as lastTime
            from chat_transcript t
            join (
                select conversation_id, max(id) as max_id
                from chat_transcript
                where user_id = #{userId}
                group by conversation_id
            ) m on t.id = m.max_id
            order by t.create_time desc
            limit #{limit}
            """)
    List<Map<String, Object>> selectConversationsByUser(@Param("userId") Long userId,
                                                        @Param("limit") int limit);
}
