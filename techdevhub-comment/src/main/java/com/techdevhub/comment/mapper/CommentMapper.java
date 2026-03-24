package com.techdevhub.comment.mapper;

import com.techdevhub.comment.entity.CommentInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("""
            insert into comment_info(id, content, user_id, blog_id, parent_id, is_delete)
            values(#{id}, #{content}, #{userId}, #{blogId}, #{parentId}, #{isDelete})
            """)
    int insert(CommentInfo commentInfo);

    @Select("""
            select id, content, user_id, blog_id, parent_id, is_delete
            from comment_info
            where id = #{id}
            limit 1
            """)
    CommentInfo selectById(Long id);

    @Select("""
            select id, content, user_id, blog_id, parent_id, is_delete
            from comment_info
            where blog_id = #{blogId} and parent_id is null and is_delete = 0
            order by id desc
            """)
    List<CommentInfo> selectRootComments(Long blogId);

    @Select("""
            select id, content, user_id, blog_id, parent_id, is_delete
            from comment_info
            where parent_id = #{parentId} and is_delete = 0
            order by id asc
            """)
    List<CommentInfo> selectChildren(Long parentId);

    @Update("""
            update comment_info
            set is_delete = 1
            where id = #{id}
            """)
    int logicDelete(Long id);
}
