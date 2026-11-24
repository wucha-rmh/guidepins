package com.guidepins.publicmap.mapper.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guidepins.publicmap.mapper.entity.MapPinRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 图钉读取 SQL 定义在 mapper/publicmap/MapPinSqlMapper.xml。 */
@Mapper
public interface MapPinSqlMapper extends BaseMapper<MapPinRow> {
    List<MapPinRow> selectByMap(@Param("mapId") UUID mapId, @Param("viewerId") UUID viewerId);
}
