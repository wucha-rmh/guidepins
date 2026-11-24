package com.guidepins.publicmap.mapper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;
import java.time.Instant;
import lombok.Data;

/** public.travel_map 数据库实体，包含访问权限和业务时间字段。 */
@Data
@TableName(value = "travel_map", schema = "public")
public class TravelMapRow {
    /** 读取 UUID；写入使用 XML 的 INSERT RETURNING 获取数据库默认主键。 */
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("city_id")
    private UUID cityId;
    private String name;
    /** 地图所有者账号 UUID。 */
    private UUID ownerId;
    /** 可见性：private 或 public。 */
    private String visibility;
    /** 地图创建时间点。 */
    private Instant createdAt;
    /** 地图实际内容或公开状态最近修改时间点。 */
    private Instant updatedAt;
}
