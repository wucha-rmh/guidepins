package com.guidepins.publicmap.service;

import com.guidepins.city.service.CityService;
import com.guidepins.exception.ResourceNotFoundException;
import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.PublicMapDetail;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 校验地市或地图存在，并把地图与所属图钉组合为只读详情。 */
@Service
public class PublicMapService {
    private final PublicMapDataMapper maps;
    private final CityService cities;

    public PublicMapService(PublicMapDataMapper maps, CityService cities) {
        this.maps = maps;
        this.cities = cities;
    }

    public List<PublicMap> listByCity(UUID cityId) {
        // 区分“地市不存在”和“地市存在但尚无公共地图”。
        cities.get(cityId);
        return maps.findByCityId(cityId);
    }

    public PublicMapDetail get(UUID id) {
        PublicMap map = maps.findById(id).orElseThrow(() -> new ResourceNotFoundException("Public map not found: " + id));
        return new PublicMapDetail(map.id(), map.cityId(), map.name(), maps.findPinsByMapId(id));
    }
}
