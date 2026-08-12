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

    @Select("""
            select blog_id
            from blog_like_info
            where user_id = #{userId} and is_delete = 0
              and blog_id in (<foreach collection='blogIds' item='id' open='' separator=',' close=''>#{id}</foreach>)
            """)
    java.util.List<Long> selectLikedBlogIds(@Param("userId") Long userId, @Param("blogIds") java.util.List<Long> blogIds);
}
