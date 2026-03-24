package com.techdevhub.mapper;

import com.techdevhub.entity.BlogInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BlogMapper {

    @Select("""
            select id, user_id, title, content, category_id, create_time, update_time,
                   like_count, view_count, comment_count, is_delete, status
            from blog_info
            where id = #{id}
            limit 1
            """)
    BlogInfo selectById(Long id);

    @Insert("""
            insert into blog_info (id, user_id, title, content, category_id)
            values (#{id}, #{userId}, #{title}, #{content}, #{categoryId})
            """)
    int insert(BlogInfo blogInfo);

    @Update("""
            update blog_info
            set title = #{title}, content = #{content}, category_id = #{categoryId}, update_time = now()
            where id = #{id}  and is_delete = 0
            """)
    int updateBlog(@Param("id") Long id, @Param("title") String title, @Param("content") String content, @Param("categoryId") Integer categoryId);

    @Update("""
            update blog_info
            set is_delete = 1, update_time = now()
            where id = #{id} and is_delete = 0
            """)
    int logicDelete(Long id);

    @Select("""
            <script>
            select count(1)
            from blog_info
            where is_delete = 0 and status = 1
            <if test="categoryId != null">
                and category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
                and title like concat('%', #{keyword}, '%')
            </if>
            <if test="userId != null">
                and user_id = #{userId}
            </if>
            </script>
            """)
    Long countPage(@Param("categoryId") Integer categoryId,
                   @Param("keyword") String keyword,
                   @Param("userId") Long userId);

    @Select("""
            <script>
            select id, user_id, title, content, category_id, create_time, update_time,
                   like_count, view_count, comment_count, is_delete, status
            from blog_info
            where is_delete = 0 and status = 1
            <if test="categoryId != null">
                and category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
                and title like concat('%', #{keyword}, '%')
            </if>
            <if test="userId != null">
                and user_id = #{userId}
            </if>
            order by view_count
            limit #{offset}, #{pageSize}
            </script>
            """)
    List<BlogInfo> selectPage(@Param("offset") Long offset, @Param("pageSize") Long pageSize, @Param("categoryId") Integer categoryId, @Param("keyword") String keyword, @Param("userId") Long userId);

    @Select("""
            select id, user_id, title, content, category_id, create_time, update_time,
                   like_count, view_count, comment_count
            from blog_info
            where is_delete = 0 and status = 1
            order by (view_count + like_count*2 + comment_count*3) desc
            limit #{limit}
            """)
    List<BlogInfo> selectTopByHot(@Param("limit") Integer limit);

    @Select("""
            select id, user_id, title, content, category_id, create_time, update_time,
                   like_count, view_count, comment_count
            from blog_info
            where user_id = #{userId} and is_delete = 0 and status = 1
            order by update_time desc
            """)
    List<BlogInfo> selectByUserId(@Param("userId") Long userId);

    @Update("""
            update blog_info
            set status = #{status}
            where id = #{id} and is_delete = 0
            """)
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Select("""
            select id, user_id, title, content, category_id, create_time, update_time,
                   like_count, view_count, comment_count
            from blog_info
            where is_delete = 0 and status = 0
            order by create_time asc
            """)
    List<BlogInfo> selectPendingBlogs();

    @Select("""
            select id
            from blog_info
            where is_delete = 0 and status = 1
            """)
    List<Long> selectAllPublishedIds();

    @Update("""
            update blog_info
            set like_count = #{likeCount}, view_count = #{viewCount}, comment_count = #{commentCount}, update_time = now()
            where id = #{id}
            """)
    int updateCounters(@Param("id") Long id, @Param("likeCount") Integer likeCount, @Param("viewCount") Integer viewCount, @Param("commentCount") Integer commentCount);
}
