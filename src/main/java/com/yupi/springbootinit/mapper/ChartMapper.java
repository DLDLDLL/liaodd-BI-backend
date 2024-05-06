package com.yupi.springbootinit.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.model.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
* @author D
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2024-03-30 21:20:43
* @Entity generator.domain.Chart
*/
public interface ChartMapper extends BaseMapper<Chart> {
    @MapKey(value = "id")
    List<Map<String,Object>> queryChartData(String querySql);

    List<Chart> selectByPage(@Param("userId") Long userId,@Param("offset")long offset,@Param("size")long size);

    long selectTotal(@Param("userId") Long userId);
}




