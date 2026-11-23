package com.guidepins.publicmap.mapper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;
import lombok.Data;

/** public.map_pin 的数据库实体；mapId 关联所属地图。 */
@Data
@TableName(value = "map_pin", schema = "public")
public class MapPinRow {
    /** 业务 UUID 由应用提供。 */
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("map_id")
    private UUID mapId;
    private String name;
    private String description;
    private double longitude;
    private double latitude;
}
