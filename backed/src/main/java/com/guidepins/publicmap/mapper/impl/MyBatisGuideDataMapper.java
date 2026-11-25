package com.guidepins.publicmap.mapper.impl;

import com.guidepins.publicmap.domain.Guide;
import com.guidepins.publicmap.domain.GuidePinLink;
import com.guidepins.publicmap.domain.GuideSummary;
import com.guidepins.publicmap.mapper.GuideDataMapper;
import com.guidepins.publicmap.mapper.entity.GuidePinLinkRow;
import com.guidepins.publicmap.mapper.entity.GuideRow;
import com.guidepins.publicmap.mapper.entity.GuideSummaryRow;
import com.guidepins.publicmap.mapper.mybatis.GuideSqlMapper;
import com.guidepins.publicmap.mapper.mybatis.MapPinSqlMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** 把攻略行与关联行转换成接口数据，写入成功后顺带推进所属地图的更新时间。 */
@Repository
public class MyBatisGuideDataMapper implements GuideDataMapper {
    @Autowired
    private GuideSqlMapper guides;

    @Autowired
    private MapPinSqlMapper pins;

    @Override
    public List<Guide> findByMapId(UUID mapId, UUID viewerId) {
        return guides.selectByMap(mapId, viewerId).stream().map(MyBatisGuideDataMapper::toGuide).toList();
    }

    @Override
    public Optional<Guide> findById(UUID mapId, UUID guideId, UUID viewerId) {
        return Optional.ofNullable(guides.selectGuide(mapId, guideId, viewerId))
                .map(MyBatisGuideDataMapper::toGuide);
    }

    @Override
    public Optional<Guide> findWritable(UUID mapId, UUID guideId, UUID actorId) {
        return Optional.ofNullable(guides.selectWritableGuide(mapId, guideId, actorId))
                .map(MyBatisGuideDataMapper::toGuide);
    }

    @Override
    public List<Guide> findByPin(UUID mapId, UUID pinId, UUID viewerId) {
        return guides.selectByPin(mapId, pinId, viewerId).stream().map(MyBatisGuideDataMapper::toGuide).toList();
    }

    @Override
    public List<GuidePinLink> findLinks(UUID mapId, UUID guideId, UUID viewerId) {
        return guides.selectLinksByGuide(mapId, guideId, viewerId).stream()
                .map(row -> new GuidePinLink(row.getPinId(), row.getPosition()))
                .toList();
    }

    @Override
    public Map<UUID, List<GuidePinLink>> findLinksByMap(UUID mapId, UUID viewerId) {
        Map<UUID, List<GuidePinLink>> grouped = new LinkedHashMap<>();
        for (GuidePinLinkRow row : guides.selectLinksByMap(mapId, viewerId)) {
            grouped.computeIfAbsent(row.getGuideId(), key -> new ArrayList<>())
                    .add(new GuidePinLink(row.getPinId(), row.getPosition()));
        }
        return grouped;
    }

    @Override
    public Map<UUID, List<GuideSummary>> findSummariesByMap(UUID mapId, UUID viewerId) {
        Map<UUID, List<GuideSummary>> grouped = new LinkedHashMap<>();
        for (GuideSummaryRow row : guides.selectSummariesByMap(mapId, viewerId)) {
            grouped.computeIfAbsent(row.getPinId(), key -> new ArrayList<>())
                    .add(new GuideSummary(row.getGuideId(), row.getTitle(), row.getSourceUrl()));
        }
        return grouped;
    }

    @Override
    public Optional<Guide> create(UUID mapId, UUID actorId, String title, String body, String sourceUrl) {
        GuideRow row = guides.insertGuide(mapId, actorId, title, body, sourceUrl);
        if (row == null) {
            return Optional.empty();
        }
        // 新增攻略总会改变地图内容，写入成功后推进地图更新时间。
        pins.touchMap(mapId, actorId);
        return Optional.of(toGuide(row));
    }

    @Override
    public Optional<Guide> update(UUID mapId, UUID guideId, UUID actorId, String title, String body,
                                  String sourceUrl) {
        // 更新时间判断需要旧值，因此必须在更新之前执行；失败时由事务整体回滚。
        guides.touchMapOnGuideChange(mapId, guideId, actorId, title, body, sourceUrl);
        return Optional.ofNullable(guides.updateGuide(mapId, guideId, actorId, title, body, sourceUrl))
                .map(MyBatisGuideDataMapper::toGuide);
    }

    @Override
    public boolean delete(UUID mapId, UUID guideId, UUID actorId) {
        if (guides.deleteGuide(mapId, guideId, actorId) == null) {
            return false;
        }
        pins.touchMap(mapId, actorId);
        return true;
    }

    @Override
    public List<UUID> findLinkablePinIds(UUID mapId, UUID guideId, UUID actorId, List<UUID> pinIds) {
        return guides.selectLinkablePinIds(mapId, guideId, actorId, pinIds);
    }

    @Override
    public void replaceLinks(UUID mapId, UUID guideId, UUID actorId, List<UUID> pinIds) {
        // 站序整体重写：先清空旧关联，再按请求顺序写入 0..n-1，避免带唯一约束时的中途冲突。
        guides.deleteLinksByGuide(mapId, guideId, actorId);
        for (int position = 0; position < pinIds.size(); position++) {
            guides.insertLink(guideId, pinIds.get(position), position);
        }
        pins.touchMap(mapId, actorId);
    }

    private static Guide toGuide(GuideRow row) {
        return new Guide(row.getId(), row.getMapId(), row.getOrigin(), row.getTitle(), row.getBody(),
                row.getSourceUrl(), row.getCreatedAt(), row.getUpdatedAt());
    }
}
