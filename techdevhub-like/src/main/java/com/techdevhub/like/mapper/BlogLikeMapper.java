package com.techdevhub.like.mapper;

import com.techdevhub.like.entity.BlogLikeInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogLikeMapper {

    @Select("""
            select id, user_id, blog_id, is_delete
            from blog_like_info
            where user_id = #{userId} and blog_id = #{blogId}
            limit 1
            """)
    BlogLikeInfo selectRelation(@Param("userId") Long userId, @Param("blogId") Long blogId);

    @Insert("""
            insert into blog_like_info(id, user_id, blog_id, is_delete)
            values(#{id}, #{userId}, #{blogId}, #{isDelete})
            """)
    int insert(BlogLikeInfo blogLikeInfo);

    @Update("""
            update blog_like_info
            set is_delete = #{isDelete}
            where id = #{id}
            """)
    int updateDeleteStatus(@Param("id") Long id, @Param("isDelete") Integer isDelete);

    /** 批量查询用户已点赞的 blogId 列表（SQL 定义在 BlogLikeMapper.xml） */
    java.util.List<Long> selectLikedBlogIds(@Param("userId") Long userId, @Param("blogIds") java.util.List<Long> blogIds);

    @Select("""
            select count(*)
            from blog_like_info
            where blog_id = #{blogId} and is_delete = 0
            """)
    Long countByBlogId(@Param("blogId") Long blogId);
}
