package com.guidepins.publicmap.mapper.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guidepins.publicmap.mapper.entity.TravelMapRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公共地图读取 SQL 定义在 mapper/publicmap/TravelMapSqlMapper.xml。 */
@Mapper
public interface TravelMapSqlMapper extends BaseMapper<TravelMapRow> {
    List<TravelMapRow> selectByCity(@Param("cityId") UUID cityId);

    TravelMapRow selectMapById(@Param("id") UUID id);
}
