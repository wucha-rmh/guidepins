package com.guidepins.publicmap.mapper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;
import lombok.Data;

/** public.travel_map 的数据库实体；cityId 关联所属地市。 */
@Data
@TableName(value = "travel_map", schema = "public")
public class TravelMapRow {
    /** 业务 UUID 由应用提供。 */
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("city_id")
    private UUID cityId;
    private String name;
}
