package com.guidepins.city.mapper.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guidepins.city.mapper.entity.CityRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 地市读取 SQL 定义在 mapper/city/CitySqlMapper.xml；继承 MyBatis-Plus 映射能力。 */
@Mapper
public interface CitySqlMapper extends BaseMapper<CityRow> {
    List<CityRow> selectAllCities();

    CityRow selectCityById(@Param("id") UUID id);
}
