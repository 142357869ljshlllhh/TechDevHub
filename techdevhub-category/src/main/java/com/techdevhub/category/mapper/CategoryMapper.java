package com.techdevhub.category.mapper;

import com.techdevhub.category.entity.CategoryInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("""
            select id, category_name, is_delete
            from category_info
            where is_delete = 0
            order by id asc
            """)
    List<CategoryInfo> selectAll();

    @Select("""
            select id, category_name, is_delete
            from category_info
            where id = #{id}
            limit 1
            """)
    CategoryInfo selectById(@Param("id") Integer id);

    @Select("""
            select id, category_name, is_delete
            from category_info
            where category_name = #{name} and is_delete = 0
            limit 1
            """)
    CategoryInfo selectByName(@Param("name") String name);

    @Insert("""
            insert into category_info (id, category_name)
            values (#{id}, #{categoryName})
            """)
    int insert(@Param("id") Integer id, @Param("categoryName") String categoryName);

    @Update("""
            update category_info
            set category_name = #{categoryName}
            where id = #{id} and is_delete = 0
            """)
    int updateName(@Param("id") Integer id, @Param("categoryName") String categoryName);

    @Update("""
            update category_info
            set is_delete = 1
            where id = #{id} and is_delete = 0
            """)
    int logicDelete(@Param("id") Integer id);
}

