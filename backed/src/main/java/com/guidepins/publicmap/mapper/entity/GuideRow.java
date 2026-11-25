package com.guidepins.publicmap.mapper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** guide 表的行映射，字段与迁移脚本一一对应。 */
@Data
@TableName(value = "guide", schema = "public")
public class GuideRow {
    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("map_id")
    private UUID mapId;

    @TableField("origin")
    private String origin;

    @TableField("title")
    private String title;

    @TableField("body")
    private String body;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
