package com.guidepins.publicmap.controller;

import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.PublicMapDetail;
import com.guidepins.publicmap.service.PublicMapService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 无需登录即可读取地市下的站点地图，以及地图上的图钉和说明。 */
@RestController
public class PublicMapController {
    @Autowired
    private PublicMapService maps;

    @GetMapping("/api/cities/{cityId}/maps")
    List<PublicMap> list(@PathVariable UUID cityId) {
        return maps.listByCity(cityId);
    }

    @GetMapping("/api/maps/{mapId}")
    PublicMapDetail get(@PathVariable UUID mapId) {
        return maps.get(mapId);
    }
}
