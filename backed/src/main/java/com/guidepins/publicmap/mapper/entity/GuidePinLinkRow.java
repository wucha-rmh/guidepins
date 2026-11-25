package com.guidepins.publicmap.mapper.entity;

import java.util.UUID;
import lombok.Data;

/** pin_guide 表的行映射：只取关联需要的三个字段，够用即可。 */
@Data
public class GuidePinLinkRow {
    private UUID guideId;
    private UUID pinId;
    private int position;
}
