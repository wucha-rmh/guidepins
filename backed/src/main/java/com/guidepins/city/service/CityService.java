package com.guidepins.city.service;

import com.guidepins.city.domain.City;
import com.guidepins.city.mapper.CityDataMapper;
import com.guidepins.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 提供地市目录，并供公共地图模块核对地市是否存在。 */
@Service
public class CityService {
    @Autowired
    private CityDataMapper cities;

    public List<City> list() {
        return cities.findAll();
    }

    public City get(UUID id) {
        return cities.findById(id).orElseThrow(() -> new ResourceNotFoundException("City not found: " + id));
    }
}
