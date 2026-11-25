package com.guidepins.publicmap.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 攻略条目：挂在某张地图上，保存标题、正文与可选的来源链接。
 * origin 现阶段固定为 manual，留出 agent 自动抓取产物的位置。
 */
public record Guide(UUID id, UUID mapId, String origin, String title, String body, String sourceUrl,
                    Instant createdAt, Instant updatedAt) {}
