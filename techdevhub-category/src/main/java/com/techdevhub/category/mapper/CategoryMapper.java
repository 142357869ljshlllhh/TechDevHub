package com.techdevhub.category.mapper;

import com.techdevhub.category.entity.CategoryInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("""
            select id, category_name, is_delete, status, creator_id, reject_reason
            from category_info
            where is_delete = 0 and status = 1
            order by id asc
            """)
    List<CategoryInfo> selectAll();

    @Select("""
            select id, category_name, is_delete, status, creator_id, reject_reason
            from category_info
            where is_delete = 0 and status = 0
            order by id asc
            """)
    List<CategoryInfo> selectPending();

    @Select("""
            select id, category_name, is_delete
            from category_info
            where id = #{id}
            limit 1
            """)
    CategoryInfo selectById(@Param("id") Long id);

    @Select("""
            select id, category_name, is_delete
            from category_info
            where category_name = #{name} and is_delete = 0
            limit 1
            """)
    CategoryInfo selectByName(@Param("name") String name);

    @Insert("""
            insert into category_info (id, category_name, status, creator_id)
            values (#{id}, #{categoryName}, #{status}, #{creatorId})
            """)
    int insert(@Param("id") Long id, @Param("categoryName") String categoryName,
               @Param("status") Integer status, @Param("creatorId") Long creatorId);

    @Update("""
            update category_info
            set category_name = #{categoryName}
            where id = #{id} and is_delete = 0
            """)
    int updateName(@Param("id") Long id, @Param("categoryName") String categoryName);

    @Update("""
            update category_info
            set is_delete = 1
            where id = #{id} and is_delete = 0
            """)
    int logicDelete(@Param("id") Long id);

    @Update("""
            update category_info
            set status = 1, reject_reason = null
            where id = #{id} and is_delete = 0
            """)
    int approve(@Param("id") Long id);

    @Update("""
            update category_info
            set status = 2, reject_reason = #{reason}
            where id = #{id} and is_delete = 0
            """)
    int reject(@Param("id") Long id, @Param("reason") String reason);
}

