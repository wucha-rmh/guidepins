package com.guidepins.publicmap.domain;

import java.util.UUID;

/** 图钉侧看到的出处摘要：只带标题与来源链接，正文通过攻略列表接口按需读取。 */
public record GuideSummary(UUID id, String title, String sourceUrl) {}
