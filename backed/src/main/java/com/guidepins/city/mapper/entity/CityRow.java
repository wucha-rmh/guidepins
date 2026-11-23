package com.guidepins.city.mapper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;
import lombok.Data;

/** public.city 的数据库实体；与对外返回的 City 分开，保留表中的四个边界列。 */
@Data
@TableName(value = "city", schema = "public")
public class CityRow {
    /** 业务 UUID 由应用提供，不使用 MyBatis-Plus 自动生成。 */
    @TableId(type = IdType.INPUT)
    private UUID id;
    private String code;
    private String name;
    /** 网站发布的 PMTiles 地址，对外返回为 basemapPath。 */
    @TableField("basemap_path")
    private String basemapPath;
    private double west;
    private double south;
    private double east;
    private double north;
}
