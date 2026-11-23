package com.guidepins.city.mapper;

import com.guidepins.city.domain.City;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 地市读取接口；业务层不依赖具体的数据库映射框架。 */
public interface CityDataMapper {
    List<City> findAll();
    Optional<City> findById(UUID id);
}
