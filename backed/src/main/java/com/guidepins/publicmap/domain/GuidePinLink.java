package com.guidepins.publicmap.domain;

import java.util.UUID;

/** 攻略与图钉的关联：position 是该图钉在这篇攻略里的站序，从 0 开始连续排列。 */
public record GuidePinLink(UUID pinId, int position) {}
