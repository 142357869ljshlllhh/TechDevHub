package com.techdevhub.mapper;

import com.techdevhub.entity.BlogModeration;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogModerationMapper {

    @Insert("""
            insert into blog_moderation
                (blog_id, verdict, confidence, reason, layer, error_code, review_reason, latency_ms, create_time)
            values
                (#{blogId}, #{verdict}, #{confidence}, #{reason}, #{layer},
                 #{errorCode}, #{reviewReason}, #{latencyMs}, #{createTime})
            """)
    int insert(BlogModeration record);
}
