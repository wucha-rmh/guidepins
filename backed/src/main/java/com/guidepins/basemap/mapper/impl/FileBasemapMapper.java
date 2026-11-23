package com.guidepins.basemap.mapper.impl;

import com.guidepins.basemap.mapper.BasemapMapper;

import com.guidepins.exception.ResourceNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

/** 从配置的本地路径读取南京 PMTiles；文件缺失时向接口层传递 404。 */
@Repository
public class FileBasemapMapper implements BasemapMapper {
    private final Path nanjingFile;

    public FileBasemapMapper(@Value("${guidepins.basemap.file}") String nanjingFile) {
        this.nanjingFile = Path.of(nanjingFile).toAbsolutePath().normalize();
    }

    @Override
    public Resource nanjing() {
        if (!Files.isRegularFile(nanjingFile) || !Files.isReadable(nanjingFile)) {
            throw new ResourceNotFoundException("Nanjing basemap file not found");
        }
        // FileSystemResource 可重复打开，支持 Spring MVC 的字节范围读取。
        return new FileSystemResource(nanjingFile);
    }
}
