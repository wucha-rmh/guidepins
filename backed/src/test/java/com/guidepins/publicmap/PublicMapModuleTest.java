package com.guidepins.publicmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidepins.city.domain.City;
import com.guidepins.city.mapper.CityDataMapper;
import com.guidepins.city.service.CityService;
import com.guidepins.exception.ResourceNotFoundException;
import com.guidepins.publicmap.controller.PublicMapController;
import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.mapper.entity.MapPinRow;
import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.PublicMapDetail;
import com.guidepins.publicmap.mapper.entity.TravelMapRow;
import com.guidepins.publicmap.mapper.mybatis.MapPinSqlMapper;
import com.guidepins.publicmap.mapper.impl.MyBatisPublicMapMapper;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import com.guidepins.publicmap.mapper.mybatis.TravelMapSqlMapper;
import com.guidepins.publicmap.service.PublicMapService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicMapModuleTest {
    private final UUID cityId = UUID.randomUUID();
    private final UUID mapId = UUID.randomUUID();
    private final PublicMap map = new PublicMap(mapId, cityId, "南京秦淮示例地图");
    private final MapPin pin = new MapPin(UUID.randomUUID(), "中华门", "城门说明", 118.77, 32.01);

    @Test
    void callsXmlMapperByParentAndMapsRows() {
        TravelMapSqlMapper maps = Mockito.mock(TravelMapSqlMapper.class);
        MapPinSqlMapper pins = Mockito.mock(MapPinSqlMapper.class);
        TravelMapRow mapRow = new TravelMapRow();
        mapRow.setId(mapId);
        mapRow.setCityId(cityId);
        mapRow.setName(map.name());
        MapPinRow pinRow = new MapPinRow();
        pinRow.setId(pin.id());
        pinRow.setMapId(mapId);
        pinRow.setName(pin.name());
        pinRow.setDescription(pin.description());
        pinRow.setLongitude(pin.longitude());
        pinRow.setLatitude(pin.latitude());
        when(maps.selectMapById(mapId)).thenReturn(mapRow);
        when(maps.selectByCity(cityId)).thenReturn(List.of(mapRow));
        when(pins.selectByMap(mapId)).thenReturn(List.of(pinRow));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TravelMapSqlMapper.class, () -> maps);
            context.registerBean(MapPinSqlMapper.class, () -> pins);
            context.register(MyBatisPublicMapMapper.class);
            context.refresh();
            MyBatisPublicMapMapper dataMapper = context.getBean(MyBatisPublicMapMapper.class);
            assertEquals(List.of(map), dataMapper.findByCityId(cityId));
            assertEquals(Optional.of(map), dataMapper.findById(mapId));
            assertEquals(List.of(pin), dataMapper.findPinsByMapId(mapId));
        }
    }

    @Test
    void combinesMapAndPinsAndRejectsUnknownCity() {
        PublicMapDataMapper dataMapper = Mockito.mock(PublicMapDataMapper.class);
        CityService cities = Mockito.mock(CityService.class);
        when(dataMapper.findById(mapId)).thenReturn(Optional.of(map));
        when(dataMapper.findPinsByMapId(mapId)).thenReturn(List.of(pin));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PublicMapDataMapper.class, () -> dataMapper);
            // 模拟服务已由测试创建，直接注册，避免 Spring 再装配其内部依赖。
            context.getBeanFactory().registerSingleton("cities", cities);
            context.register(PublicMapService.class);
            context.refresh();
            PublicMapService service = context.getBean(PublicMapService.class);
            assertEquals(new PublicMapDetail(mapId, cityId, map.name(), List.of(pin)), service.get(mapId));

            when(cities.get(cityId)).thenThrow(new ResourceNotFoundException("City not found"));
            assertThrows(ResourceNotFoundException.class, () -> service.listByCity(cityId));
        }
    }

    @Test
    void servesMapJsonAndReturns404ForUnknownMap() throws Exception {
        PublicMapDataMapper dataMapper = Mockito.mock(PublicMapDataMapper.class);
        CityDataMapper cityMapper = Mockito.mock(CityDataMapper.class);
        when(cityMapper.findById(cityId)).thenReturn(Optional.of(new City(cityId, "320100", "南京",
                "/maps/nanjing/v1/nanjing-z16.pmtiles", new City.Bounds(118.3, 31.2, 119.2, 32.6))));
        when(dataMapper.findByCityId(cityId)).thenReturn(List.of(map));
        when(dataMapper.findById(mapId)).thenReturn(Optional.of(map));
        when(dataMapper.findPinsByMapId(mapId)).thenReturn(List.of(pin));
        // 使用真实服务装配跨模块依赖，接口断言同时覆盖字段注入是否生效。
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PublicMapDataMapper.class, () -> dataMapper);
            context.registerBean(CityDataMapper.class, () -> cityMapper);
            context.register(PublicMapController.class, PublicMapService.class, CityService.class);
            context.refresh();
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(PublicMapController.class)).build();

            mvc.perform(get("/api/cities/{cityId}/maps", cityId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value(map.name()));
            mvc.perform(get("/api/maps/{mapId}", mapId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pins[0].description").value(pin.description()));
            mvc.perform(get("/api/maps/{mapId}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }
}
