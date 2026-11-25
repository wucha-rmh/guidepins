package com.guidepins.publicmap.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 攻略详情：正文、来源链接，以及按站序排列的关联图钉编号。 */
public record GuideDetail(UUID id, UUID mapId, String origin, String title, String body, String sourceUrl,
                          Instant createdAt, Instant updatedAt, List<GuidePinLink> pins) {}
