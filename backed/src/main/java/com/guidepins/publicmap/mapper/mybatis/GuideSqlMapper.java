package com.guidepins.publicmap.mapper.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guidepins.publicmap.mapper.entity.GuidePinLinkRow;
import com.guidepins.publicmap.mapper.entity.GuideRow;
import com.guidepins.publicmap.mapper.entity.GuideSummaryRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 攻略与关联的 SQL 入口，语句定义在 mapper/publicmap/GuideSqlMapper.xml。
 * 读取语句内联可见性条件，写入语句内联「所有者，或管理员且地图公开」条件。
 */
@Mapper
public interface GuideSqlMapper extends BaseMapper<GuideRow> {
    List<GuideRow> selectByMap(@Param("mapId") UUID mapId, @Param("viewerId") UUID viewerId);

    GuideRow selectGuide(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId,
                         @Param("viewerId") UUID viewerId);

    /* 写入前的一次可写性读取，让服务层能把「写不进去」翻译成 403 或 404。 */
    GuideRow selectWritableGuide(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId,
                                 @Param("actorId") UUID actorId);

    List<GuideRow> selectByPin(@Param("mapId") UUID mapId, @Param("pinId") UUID pinId,
                               @Param("viewerId") UUID viewerId);

    List<GuidePinLinkRow> selectLinksByGuide(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId,
                                             @Param("viewerId") UUID viewerId);

    List<GuidePinLinkRow> selectLinksByMap(@Param("mapId") UUID mapId, @Param("viewerId") UUID viewerId);

    List<GuideSummaryRow> selectSummariesByMap(@Param("mapId") UUID mapId, @Param("viewerId") UUID viewerId);

    GuideRow insertGuide(@Param("mapId") UUID mapId, @Param("actorId") UUID actorId, @Param("title") String title,
                         @Param("body") String body, @Param("sourceUrl") String sourceUrl);

    GuideRow updateGuide(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId, @Param("actorId") UUID actorId,
                         @Param("title") String title, @Param("body") String body,
                         @Param("sourceUrl") String sourceUrl);

    UUID deleteGuide(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId, @Param("actorId") UUID actorId);

    /* 只返回当前操作者确实可写、且属于该地图的图钉编号，用于判断请求里的图钉是否全都合法。 */
    List<UUID> selectLinkablePinIds(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId,
                                    @Param("actorId") UUID actorId, @Param("pinIds") List<UUID> pinIds);

    int deleteLinksByGuide(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId,
                           @Param("actorId") UUID actorId);

    int insertLink(@Param("guideId") UUID guideId, @Param("pinId") UUID pinId, @Param("position") int position);

    /* 攻略内容确实变化时才推进地图更新时间；带 RETURNING，未命中返回 null，故用包装类型。 */
    Integer touchMapOnGuideChange(@Param("mapId") UUID mapId, @Param("guideId") UUID guideId,
                                  @Param("actorId") UUID actorId, @Param("title") String title,
                                  @Param("body") String body, @Param("sourceUrl") String sourceUrl);
}
