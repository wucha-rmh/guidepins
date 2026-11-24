package com.guidepins.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidepins.city.controller.CityController;
import com.guidepins.city.domain.City;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidepins.config.ApiResponseAdvice;
import com.guidepins.exception.ApiExceptionHandler;
import com.guidepins.city.mapper.entity.CityRow;
import com.guidepins.city.mapper.mybatis.CitySqlMapper;
import com.guidepins.city.mapper.CityDataMapper;
import com.guidepins.city.mapper.impl.MyBatisCityMapper;
import com.guidepins.city.service.CityService;
import com.guidepins.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CityModuleTest {
    private final UUID cityId = UUID.randomUUID();
    private final City city = new City(cityId, "320100", "南京", "/maps/nanjing/v1/nanjing-z16.pmtiles",
            new City.Bounds(118.3, 31.2, 119.2, 32.6));

    @Test
    void mapsMyBatisRowToCityWithBounds() {
        CitySqlMapper mapper = Mockito.mock(CitySqlMapper.class);
        CityRow row = new CityRow();
        row.setId(cityId);
        row.setCode("320100");
        row.setName("南京");
        row.setBasemapPath(city.basemapPath());
        row.setWest(118.3);
        row.setSouth(31.2);
        row.setEast(119.2);
        row.setNorth(32.6);
        when(mapper.selectCityById(cityId)).thenReturn(row);
        when(mapper.selectAllCities()).thenReturn(List.of(row));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CitySqlMapper.class, () -> mapper);
            context.register(MyBatisCityMapper.class);
            context.refresh();
            MyBatisCityMapper dataMapper = context.getBean(MyBatisCityMapper.class);
            assertEquals(city, dataMapper.findById(cityId).orElseThrow());
            assertEquals(List.of(city), dataMapper.findAll());
        }
    }

    @Test
    void reportsMissingCity() {
        CityDataMapper dataMapper = Mockito.mock(CityDataMapper.class);
        when(dataMapper.findById(cityId)).thenReturn(Optional.empty());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CityDataMapper.class, () -> dataMapper);
            context.register(CityService.class);
            context.refresh();
            assertThrows(ResourceNotFoundException.class, () -> context.getBean(CityService.class).get(cityId));
        }
    }

    @Test
    void returnsCityDirectoryJson() throws Exception {
        CityDataMapper dataMapper = Mockito.mock(CityDataMapper.class);
        when(dataMapper.findAll()).thenReturn(List.of(city));
        // 由 Spring 注入控制器和服务的依赖，同时保留数据库边界的模拟对象。
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CityDataMapper.class, () -> dataMapper);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(CityController.class, CityService.class, ApiResponseAdvice.class);
            context.refresh();
            MockMvcBuilders.standaloneSetup(context.getBean(CityController.class))
                    .setControllerAdvice(context.getBean(ApiResponseAdvice.class), new ApiExceptionHandler()).build()
                    .perform(get("/api/cities"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].code").value("320100"))
                    .andExpect(jsonPath("$.data[0].basemapPath").value(city.basemapPath()))
                    .andExpect(jsonPath("$.data[0].bounds.west").value(118.3));
        }
    }
}
