package com.guidepins.publicmap.service;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.exception.ApiException;
import com.guidepins.exception.ResourceNotFoundException;
import com.guidepins.publicmap.domain.ManagedMap;
import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图钉编辑业务：校验客户端字段，把写入结果翻译成 403 或 404。
 * 权限判断本身留在写入 SQL 里，服务层只负责把“写不进去”解释成用户能看懂的状态。
 */
@Service
public class PinEditingService {
    private static final int NAME_LIMIT = 120;
    private static final int DESCRIPTION_LIMIT = 2000;

    @Autowired
    private PublicMapDataMapper maps;

    /** 落钉：图钉归属由路径中的地图决定，客户端不能指定所属地图。 */
    @Transactional
    public MapPin create(UUID mapId, String name, String description, Double longitude, Double latitude,
                         AccountIdentity user) {
        requireUser(user);
        String pinName = requireName(name);
        String pinDescription = normalizeDescription(description);
        requireCoordinates(longitude, latitude);
        return maps.insertPin(mapId, user.id(), pinName, pinDescription, longitude, latitude)
                .orElseGet(() -> {
                    // 地图不可见时返回 404；可见但无写权限时返回 403，与地图改名保持一致。
                    readable(mapId, user.id());
                    throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该地图的权限");
                });
    }

    /** 修改图钉：字段整体替换，说明为空表示清空说明。 */
    @Transactional
    public MapPin update(UUID mapId, UUID pinId, String name, String description, Double longitude, Double latitude,
                         AccountIdentity user) {
        requireUser(user);
        String pinName = requireName(name);
        String pinDescription = normalizeDescription(description);
        requireCoordinates(longitude, latitude);
        return maps.updatePin(mapId, pinId, user.id(), pinName, pinDescription, longitude, latitude)
                .orElseGet(() -> {
                    // 先确认地图对当前用户可见，再区分“图钉不存在”和“有权限看但没权限改”。
                    readable(mapId, user.id());
                    if (maps.findPin(mapId, pinId, user.id()).isEmpty()) {
                        throw new ResourceNotFoundException("Pin not found");
                    }
                    throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该图钉的权限");
                });
    }

    /** 删钉：删除后由数据层推进所属地图的更新时间。 */
    @Transactional
    public void delete(UUID mapId, UUID pinId, AccountIdentity user) {
        requireUser(user);
        if (!maps.deletePin(mapId, pinId, user.id())) {
            readable(mapId, user.id());
            if (maps.findPin(mapId, pinId, user.id()).isEmpty()) {
                throw new ResourceNotFoundException("Pin not found");
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该图钉的权限");
        }
    }

    private void requireUser(AccountIdentity user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
    }

    private String requireName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图钉名称不能为空");
        }
        if (value.length() > NAME_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图钉名称不能超过 " + NAME_LIMIT + " 个字符");
        }
        return value;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String value = description.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > DESCRIPTION_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图钉说明不能超过 " + DESCRIPTION_LIMIT + " 个字符");
        }
        return value;
    }

    private void requireCoordinates(Double longitude, Double latitude) {
        if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "经度需在 -180 至 180 之间");
        }
        if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "纬度需在 -90 至 90 之间");
        }
    }

    private ManagedMap readable(UUID mapId, UUID viewerId) {
        return maps.findById(mapId, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Map not found"));
    }
}
