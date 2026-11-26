package com.guidepins.offline.service;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.city.service.CityService;
import com.guidepins.exception.ApiException;
import com.guidepins.exception.ResourceNotFoundException;
import com.guidepins.offline.domain.OfflineSnapshot;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import com.guidepins.publicmap.service.GuideService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 在短只读事务里取得一致快照；文件裁剪与打包在事务结束后进行。 */
@Service
public class OfflineSnapshotService {
    @Autowired private PublicMapDataMapper maps;
    @Autowired private GuideService guides;
    @Autowired private CityService cities;
    @Autowired private Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OfflineSnapshot read(UUID mapId, AccountIdentity viewer) {
        UUID viewerId = viewer == null ? null : viewer.id();
        var map = maps.findById(mapId, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("地图不存在或不可访问"));
        var city = cities.get(map.cityId());
        if (!"320100".equals(city.code())) throw new ApiException(HttpStatus.BAD_REQUEST, "目前仅支持南京地图离线下载");
        return new OfflineSnapshot(1, map.id(), map.name(), map.updatedAt(), clock.instant(), city,
                maps.findPinsByMapId(mapId, viewerId), guides.list(mapId, viewer));
    }
}
