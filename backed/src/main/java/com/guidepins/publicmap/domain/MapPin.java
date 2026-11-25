package com.guidepins.publicmap.domain;

import java.util.List;
import java.util.UUID;

/**
 * 对外展示的图钉：WGS84 坐标按经度、纬度顺序返回。
 * guides 是提到了该图钉的攻略摘要（阶段三），写入接口返回时为空列表。
 */
public record MapPin(UUID id, String name, String description, double longitude, double latitude,
                     List<GuideSummary> guides) {}
