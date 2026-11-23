package com.guidepins.city.mapper.impl;

import com.guidepins.city.mapper.CityDataMapper;
import com.guidepins.city.domain.City;
import com.guidepins.city.mapper.entity.CityRow;
import com.guidepins.city.mapper.mybatis.CitySqlMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 将数据库行转换为接口使用的地市数据，按行政区划代码稳定排序。 */
@Repository
public class MyBatisCityMapper implements CityDataMapper {
    private final CitySqlMapper mapper;

    public MyBatisCityMapper(CitySqlMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<City> findAll() {
        return mapper.selectAllCities()
                .stream().map(MyBatisCityMapper::toCity).toList();
    }

    @Override
    public Optional<City> findById(UUID id) {
        return Optional.ofNullable(mapper.selectCityById(id)).map(MyBatisCityMapper::toCity);
    }

    private static City toCity(CityRow row) {
        // 数据库存四个边界列，接口将它们组合成 bounds 供地图初始化视野使用。
        return new City(row.getId(), row.getCode(), row.getName(), row.getBasemapPath(),
                new City.Bounds(row.getWest(), row.getSouth(), row.getEast(), row.getNorth()));
    }
}
