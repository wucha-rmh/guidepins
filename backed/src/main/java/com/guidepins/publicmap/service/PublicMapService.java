package com.guidepins.publicmap.service;

import com.guidepins.city.service.CityService;
import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.exception.ApiException;
import com.guidepins.exception.ResourceNotFoundException;
import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.ManagedMap;
import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.domain.GuideSummary;
import com.guidepins.publicmap.domain.PublicMapDetail;
import com.guidepins.publicmap.mapper.GuideDataMapper;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 地图业务与权限入口；当前只写地图元数据，图钉编辑留在后续阶段。 */
@Service
public class PublicMapService {
    @Autowired
    private PublicMapDataMapper maps;

    @Autowired
    private GuideDataMapper guides;

    @Autowired
    private CityService cities;

    public List<PublicMap> listByCity(UUID cityId) {
        // 区分“地市不存在”和“地市存在但尚无公共地图”。
        cities.get(cityId);
        return maps.findByCityId(cityId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicMapDetail get(UUID id, AccountIdentity viewer) {
        UUID viewerId = viewer == null ? null : viewer.id();
        // 地图、图钉与出处摘要在同一快照内读取；不存在和无读取权限都返回 404。
        ManagedMap map = readable(id, viewerId);
        Map<UUID, List<GuideSummary>> summaries = guides.findSummariesByMap(id, viewerId);
        List<MapPin> pins = maps.findPinsByMapId(id, viewerId).stream()
                .map(pin -> new MapPin(pin.id(), pin.name(), pin.description(), pin.longitude(), pin.latitude(),
                        summaries.getOrDefault(pin.id(), List.of())))
                .toList();
        return new PublicMapDetail(map.id(), map.cityId(), map.name(), pins);
    }

    @Transactional(readOnly = true)
    public List<ManagedMap> owned(UUID cityId, AccountIdentity user) {
        requireUser(user);
        cities.get(cityId);
        return maps.findOwnedByCity(cityId, user.id());
    }

    @Transactional
    public ManagedMap create(UUID cityId, String name, String visibility, AccountIdentity user) {
        requireUser(user);
        cities.get(cityId);
        return maps.create(cityId, user.id(), name, visibility)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号不可用"));
    }

    @Transactional
    public ManagedMap rename(UUID id, String name, AccountIdentity user) {
        requireUser(user);
        return maps.updateName(id, user.id(), name).orElseGet(() -> {
            readable(id, user.id());
            throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该地图的权限");
        });
    }

    @Transactional
    public ManagedMap changeVisibility(UUID id, String visibility, AccountIdentity user) {
        requireUser(user);
        return maps.updateVisibility(id, user.id(), visibility).orElseGet(() -> {
            readable(id, user.id());
            throw new ApiException(HttpStatus.FORBIDDEN, "只有所有者可以修改公开状态");
        });
    }

    private ManagedMap readable(UUID id, UUID viewerId) {
        return maps.findById(id, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Map not found"));
    }

    private void requireUser(AccountIdentity user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
    }
}
