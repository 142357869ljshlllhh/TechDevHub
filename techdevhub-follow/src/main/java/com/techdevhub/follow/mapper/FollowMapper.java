package com.techdevhub.follow.mapper;

import com.techdevhub.follow.entity.FollowInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FollowMapper {

    @Select("""
            select id, user_id, follow_user_id, is_delete
            from follow_info
            where user_id = #{userId} and follow_user_id = #{followUserId}
            limit 1
            """)
    FollowInfo selectRelation(@Param("userId") Long userId, @Param("followUserId") Long followUserId);

    @Insert("""
            insert into follow_info(id, user_id, follow_user_id, is_delete)
            values(#{id}, #{userId}, #{followUserId}, #{isDelete})
            """)
    int insert(FollowInfo followInfo);

    @Update("""
            update follow_info
            set is_delete = #{isDelete}
            where id = #{id}
            """)
    int updateDeleteStatus(@Param("id") Long id, @Param("isDelete") Integer isDelete);
}
