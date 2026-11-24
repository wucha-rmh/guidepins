package com.guidepins.publicmap.controller;

import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.PublicMapDetail;
import com.guidepins.publicmap.service.PublicMapService;
import com.guidepins.auth.domain.AccountIdentity;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 公开列表无需登录；详情由服务层按会话身份判断私人地图访问权限。 */
@RestController
public class PublicMapController {
    @Autowired
    private PublicMapService maps;

    @GetMapping("/api/cities/{cityId}/maps")
    List<PublicMap> list(@PathVariable UUID cityId) {
        return maps.listByCity(cityId);
    }

    @GetMapping("/api/maps/{mapId}")
    PublicMapDetail get(@PathVariable UUID mapId, @AuthenticationPrincipal AccountIdentity user) {
        return maps.get(mapId, user);
    }
}
