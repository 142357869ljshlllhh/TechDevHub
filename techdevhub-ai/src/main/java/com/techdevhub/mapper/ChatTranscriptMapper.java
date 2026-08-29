package com.techdevhub.mapper;

import com.techdevhub.entity.ChatTranscript;
import org.apache.ibatis.annotations.*;

import java.util.List;

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

    /** 删除会话的全部消息明细（删除会话时由 Controller 连带调用，归属校验在注册表删除处已做） */
    @Delete("""
            delete from chat_transcript
            where conversation_id = #{conversationId} and user_id = #{userId}
            """)
    int deleteByConversation(@Param("conversationId") String conversationId,
                             @Param("userId") Long userId);
}
