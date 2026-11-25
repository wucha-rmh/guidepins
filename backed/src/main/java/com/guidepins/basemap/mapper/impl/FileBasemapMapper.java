package com.guidepins.basemap.mapper.impl;

import com.guidepins.basemap.mapper.BasemapMapper;
import com.guidepins.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

/**
 * 从本地制图目录读取各地市的 PMTiles 与边界文件。
 *
 * <p>目录约定：根目录下按「行政区代码-市名」分文件夹，例如 {@code 320100-南京市}；
 * 每个文件夹内含一份 {@code *.pmtiles} 和一份 {@code boundary.geojson}。
 * 解析结果按代码缓存，文件缺失时向接口层传递 404，不影响其他地市。
 */
@Repository
public class FileBasemapMapper implements BasemapMapper {
    /** 行政区代码是六位数字，用来挡住路径穿越和格式错误的入参。 */
    private static final Pattern CODE = Pattern.compile("\\d{6}");
    private static final String BOUNDARY_FILE = "boundary.geojson";

    private final Path root;
    /** 代码到市目录的解析结果；目录内容在运行期不变，缓存后省去重复扫描。 */
    private final Map<String, Path> cityDirectories = new ConcurrentHashMap<>();
    private final Map<String, Path> pmtilesFiles = new ConcurrentHashMap<>();

    public FileBasemapMapper(@Value("${guidepins.basemap.root}") String root) {
        this.root = resolveRoot(root);
    }

    /**
     * 相对路径按启动目录解析。仓库根目录与 backed 目录启动都被文档支持，
     * 所以自当前目录向上探测两级，取第一个真实存在的候选目录。
     */
    private static Path resolveRoot(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute()) return path.toAbsolutePath().normalize();
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 3 && cursor != null; depth += 1) {
            Path candidate = cursor.resolve(path).normalize();
            if (Files.isDirectory(candidate)) return candidate;
            cursor = cursor.getParent();
        }
        return path.toAbsolutePath().normalize();
    }

    @Override
    public Resource pmtiles(String code) {
        if (!isKnownCode(code)) throw new ResourceNotFoundException("Basemap not found: " + code);
        Path file = pmtilesFiles.computeIfAbsent(code, key -> locatePmtiles(cityDirectory(key), key));
        // FileSystemResource 可重复打开，支持 Spring MVC 的字节范围读取。
        return new FileSystemResource(file);
    }

    @Override
    public Resource boundary(String code) {
        if (!isKnownCode(code)) throw new ResourceNotFoundException("City boundary not found: " + code);
        Path file = cityDirectory(code).resolve(BOUNDARY_FILE);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResourceNotFoundException("City boundary not found: " + code);
        }
        return new FileSystemResource(file);
    }

    private boolean isKnownCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    /** 找出「代码-市名」这样的市目录；找不到时说明数据尚未制包。 */
    private Path cityDirectory(String code) {
        Path cached = cityDirectories.get(code);
        if (cached != null) return cached;
        if (!Files.isDirectory(root)) throw new ResourceNotFoundException("Basemap root not found");
        try (Stream<Path> children = Files.list(root)) {
            Path found = children
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(code + "-"))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Basemap directory not found: " + code));
            cityDirectories.put(code, found);
            return found;
        } catch (IOException error) {
            throw new ResourceNotFoundException("Basemap directory unreadable: " + code);
        }
    }

    /** 每个市目录只应有一份地图包；出现零份或多份都视为数据未就绪。 */
    private Path locatePmtiles(Path directory, String code) {
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> files = children
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".pmtiles"))
                    .toList();
            if (files.size() != 1) throw new ResourceNotFoundException("Basemap file not found: " + code);
            return files.get(0);
        } catch (IOException error) {
            throw new ResourceNotFoundException("Basemap file unreadable: " + code);
        }
    }
}
