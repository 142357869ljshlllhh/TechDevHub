package com.techdevhub.mapper;

import com.techdevhub.entity.RagIndexStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RagIndexStatusMapper {

    @Insert("""
            insert into rag_index_status (blog_id, status, error_code)
            values (#{blogId}, #{status}, #{errorCode})
            on duplicate key update status = values(status), error_code = values(error_code)
            """)
    int upsert(@Param("blogId") Long blogId,
               @Param("status") String status,
               @Param("errorCode") String errorCode);

    /** 状态列表（可选过滤），join blog_info 带标题；未删除文章 */
    @Select("""
            <script>
            select s.blog_id, s.status, s.error_code, s.update_time, b.title
            from rag_index_status s
            join blog_info b on b.id = s.blog_id and b.is_delete = 0
            <if test="status != null"> where s.status = #{status}</if>
            order by s.update_time desc
            limit 100
            </script>
            """)
    List<RagIndexStatus> selectList(@Param("status") String status);
}
