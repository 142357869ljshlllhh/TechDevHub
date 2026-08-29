package com.techdevhub.mapper;


import com.techdevhub.entity.UserInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("""
            select id,username,email,password,is_delete,status,create_time,following_count,follower_count
            from user_info
            where id = #{id}
            limit 1
            """)
    UserInfo  selectUserById(Long id);

    @Select("""
            select id,username,email,password,is_delete,status,create_time,following_count,follower_count
            from user_info
            where username = #{username}
            limit 1
            """)
    UserInfo  selectUserByUsername(String username);

    @Select("""
            select id,username,email,password,is_delete,status,create_time,following_count,follower_count
            from user_info
            where email = #{email}
            limit 1
            """)
    UserInfo  selectUserByEmail(String email);

    @Select("""
            select id,username,email,password,is_delete,status,create_time,following_count,follower_count
            from user_info
            where is_delete = 0 and username like concat('%', #{keyword}, '%')
            order by create_time asc
            limit 50
            """)
    java.util.List<UserInfo> searchByUsername(@Param("keyword") String keyword);

    @Insert("""
            insert into user_info
            (id,email,username,password,is_delete,status)
            values(#{id},#{email},#{username},#{password},0,0)
            """)
    int register(@Param("id") Long id, @Param("username") String username, @Param("password") String password,@Param("email") String email);

    @Update("""
            update user_info 
            set username = #{username},email = #{email}
            where id = #{id} and is_delete = 0
            """)
    int updateInformation(@Param("id") Long id, @Param("username")  String username, @Param("email") String email);

    @Update("""
            update user_info
            set password = #{password}
            where id = #{id} and is_delete = 0
            """)
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("""
            update user_info
            set is_delete = 1, username = #{username}, email = #{email}
            where id = #{id} and is_delete = 0
            """)
    int logicDelete(@Param("id") Long id, @Param("username") String username, @Param("email") String email);

    @Update("""
            update user_info
            set is_delete = 2
            where id = #{id} and is_delete = 0
            """)
    int banUser(@Param("id") Long id);

    @Update("""
            update user_info
            set is_delete = 0
            where id = #{id} and is_delete = 2
            """)
    int unbanUser(@Param("id") Long id);

    @Select("<script>" +
            "select id, username, is_delete, status " +
            "from user_info " +
            "where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<UserInfo> batchSelectByIds(@Param("ids") List<Long> ids);

}

