package com.guidepins.publicmap.mapper;

import com.guidepins.publicmap.domain.Guide;
import com.guidepins.publicmap.domain.GuidePinLink;
import com.guidepins.publicmap.domain.GuideSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 攻略与关联的数据访问接口。
 * 读取按「公开或本人」限定，写入按「所有者，或管理员且地图公开」限定，条件都在 SQL 里。
 */
public interface GuideDataMapper {
    List<Guide> findByMapId(UUID mapId, UUID viewerId);

    Optional<Guide> findById(UUID mapId, UUID guideId, UUID viewerId);

    /* 写入前的可写性读取；为空说明当前账号不能改这篇攻略。 */
    Optional<Guide> findWritable(UUID mapId, UUID guideId, UUID actorId);

    List<Guide> findByPin(UUID mapId, UUID pinId, UUID viewerId);

    List<GuidePinLink> findLinks(UUID mapId, UUID guideId, UUID viewerId);

    /* 按攻略编号分组，供列表接口一次取全。 */
    Map<UUID, List<GuidePinLink>> findLinksByMap(UUID mapId, UUID viewerId);

    /* 按图钉编号分组，供地图详情内嵌出处。 */
    Map<UUID, List<GuideSummary>> findSummariesByMap(UUID mapId, UUID viewerId);

    Optional<Guide> create(UUID mapId, UUID actorId, String title, String body, String sourceUrl);

    Optional<Guide> update(UUID mapId, UUID guideId, UUID actorId, String title, String body, String sourceUrl);

    boolean delete(UUID mapId, UUID guideId, UUID actorId);

    List<UUID> findLinkablePinIds(UUID mapId, UUID guideId, UUID actorId, List<UUID> pinIds);

    /* 用一次整体替换实现站序：先清空旧关联，再按数组下标写入 0..n-1。 */
    void replaceLinks(UUID mapId, UUID guideId, UUID actorId, List<UUID> pinIds);
}
