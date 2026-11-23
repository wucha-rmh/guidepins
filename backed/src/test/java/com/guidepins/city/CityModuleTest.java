package com.guidepins.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidepins.city.controller.CityController;
import com.guidepins.city.domain.City;
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

        MyBatisCityMapper dataMapper = new MyBatisCityMapper(mapper);
        assertEquals(city, dataMapper.findById(cityId).orElseThrow());
        assertEquals(List.of(city), dataMapper.findAll());
    }

    @Test
    void reportsMissingCity() {
        CityDataMapper dataMapper = Mockito.mock(CityDataMapper.class);
        when(dataMapper.findById(cityId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> new CityService(dataMapper).get(cityId));
    }

    @Test
    void returnsCityDirectoryJson() throws Exception {
        CityDataMapper dataMapper = Mockito.mock(CityDataMapper.class);
        when(dataMapper.findAll()).thenReturn(List.of(city));
        MockMvcBuilders.standaloneSetup(new CityController(new CityService(dataMapper))).build()
                .perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("320100"))
                .andExpect(jsonPath("$[0].basemapPath").value(city.basemapPath()))
                .andExpect(jsonPath("$[0].bounds.west").value(118.3));
    }
}
