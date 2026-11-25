package com.guidepins.publicmap.mapper.entity;

import java.util.UUID;
import lombok.Data;

/** 图钉侧出处摘要的行映射：一次查出整张地图的关联，再在内存里按图钉分组。 */
@Data
public class GuideSummaryRow {
    private UUID pinId;
    private UUID guideId;
    private String title;
    private String sourceUrl;
}
