"""南京地铁展示单线化：从本地 OSM 方向关系提取一条完整方向，不以站点直线代替轨道。

默认只分析；--apply 备份并替换南京 GeoJSON。之后用现有 build_tiles.py 仅重制南京。
依赖现有制图目录中的 osmium、shapely，不访问网络，不改数据库或其他城市。
"""
from pathlib import Path
import argparse
import collections
import gzip
import hashlib
import json
import math
import shutil
import subprocess
import sys

WORKSPACE = Path(__file__).resolve().parents[2]
ROOT = WORKSPACE / 'map/江苏省地图数据'
sys.path.insert(0, str(ROOT / '工具/python'))
from pmtiles.reader import Reader, MmapSource, all_tiles
import osmium
import mapbox_vector_tile
from shapely import line_merge, union_all
from shapely.geometry import LineString, MultiLineString, mapping, shape


def line_parts(geometry):
    if geometry.is_empty:
        return []
    if geometry.geom_type == 'LineString':
        return [geometry]
    return [part for child in getattr(geometry, 'geoms', []) for part in line_parts(child)]


def refresh_manifest(directory):
    """制包后集中核对未改动内容，并刷新本市校验清单，不沿用旧归档的抽样结论。"""
    old = json.loads((directory / '备份/地铁单线化前/basemap.geojson').read_text(encoding='utf-8'))
    current = json.loads((directory / 'basemap.geojson').read_text(encoding='utf-8'))
    unchanged = lambda data: [f for f in data['features'] if f['properties']['layer'] != 'metro_line']
    if unchanged(old) != unchanged(current):
        raise RuntimeError('非地铁线路数据发生变化，停止更新清单')
    lines = [f for f in current['features'] if f['properties']['layer'] == 'metro_line']
    if len(lines) != 16 or any(f['geometry']['type'] != 'LineString' for f in lines):
        raise RuntimeError('南京线路尚未全部归并为单一方向几何')
    old_lines = {f['properties']['line_id']: shape(f['geometry']) for f in old['features']
                 if f['properties']['layer'] == 'metro_line'}
    for feature in lines:
        if not old_lines[feature['properties']['line_id']].buffer(1e-9).covers(shape(feature['geometry'])):
            raise RuntimeError('单线化结果超出原有轨道范围')
    counts = collections.Counter(f['properties']['layer'] for f in current['features'])
    if counts['metro_station'] != 257:
        raise RuntimeError('站点数量不一致')
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    for name, entry in manifest['files'].items():
        if name.startswith('地铁/') and name != '地铁/lines.geojson':
            if hashlib.sha256((directory / name).read_bytes()).hexdigest() != entry['sha256']:
                raise RuntimeError(f'站点、出入口或站序文件发生变化：{name}')
    archive = directory / 'nanjing-z16.pmtiles'
    with archive.open('rb') as source:
        read = MmapSource(source)
        zoom_counts = collections.Counter(zxy[0] for zxy, blob in all_tiles(read))
        if sum(zoom_counts.values()) != Reader(read).header()['addressed_tiles_count']:
            raise RuntimeError('瓦片数量与归档头不一致')
        reader = Reader(read)
        stations = json.loads((directory / '地铁/stations.geojson').read_text(encoding='utf-8'))['features']
        cache, verified = {}, {}
        for z in (12, 14, 16):
            for station in stations:
                lon, lat = station['geometry']['coordinates']
                x = int((lon + 180) / 360 * (1 << z))
                y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * (1 << z))
                key = (z, x, y)
                if key not in cache:
                    tile = reader.get(*key)
                    decoded = mapbox_vector_tile.decode(gzip.decompress(tile)) if tile else {}
                    cache[key] = {item['properties']['station_id'] for item in decoded.get('metro_station', {}).get('features', [])}
                if station['properties']['station_id'] not in cache[key]:
                    raise RuntimeError(f"z{z} 缺少站点：{station['properties']['name']}")
            verified[str(z)] = len(stations)
    for name in ('basemap.geojson', 'nanjing-z16.pmtiles', '地铁/lines.geojson'):
        file = directory / name
        manifest['files'][name] = {'bytes': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
    for prefix, name in [('pmtiles', 'nanjing-z16.pmtiles'), ('basemap', 'basemap.geojson')]:
        for field in ('bytes', 'sha256'):
            manifest[f'{prefix}_{field}'] = manifest['files'][name][field]
    manifest['feature_counts'] = dict(counts)
    manifest['tile_count'] = sum(zoom_counts.values())
    manifest['tiles_by_zoom'] = {str(z): count for z, count in sorted(zoom_counts.items())}
    manifest['metro_display'] = {'geometry': 'single_direction', 'visible_regular_lines': 15,
                                 'through_express_overlay': False, 'stations_unchanged': 257, 'known_partial_line': 'S2'}
    manifest['metro_display']['station_tiles_verified'] = verified
    previous = manifest.setdefault('previous_tile_checks', {})
    for key in ('sample_decoded_tiles', 'metro_point_tile_checks', 'qinhuai_comparison'):
        if key in manifest:
            previous[key] = manifest.pop(key)
    (directory / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'non_metro_features_unchanged': len(unchanged(current)), 'line_strings': len(lines),
                      'stations_unchanged': counts['metro_station'], 'tiles': sum(zoom_counts.values()),
                      'pmtiles_bytes': archive.stat().st_size, 'station_tiles_verified': verified}, ensure_ascii=False), flush=True)


def build(directory):
    """沿用既有图层配置，只修正地铁站点的尺寸过滤；点没有面积，不能按面要素过滤。"""
    sys.path.insert(0, str(ROOT / '处理脚本'))
    from build_tiles import schema
    from common import load_cities
    city = next(city for city in load_cities() if city['code'] == '320100')
    config = schema(city)
    for layer in config['layers']:
        if layer['id'] == 'metro_station':
            for feature in layer['features']:
                feature['min_size'] = 0
    config_file = directory / 'planetiler.yml'
    config_file.write_text('# 南京单线地铁；站点从其 minzoom 起保留，不按面积过滤。\n' +
                           json.dumps(config, ensure_ascii=False, indent=2), encoding='utf-8')
    command = ['java', '--enable-native-access=ALL-UNNAMED', '-Xmx5g', '-jar',
               str(ROOT / '工具/planetiler-0.10.2.jar'), str(config_file), '--minzoom=7', '--maxzoom=16',
               '--threads=8', '--storage=ram', '--mmap_temp=false',
               '--bounds=' + ','.join(map(str, city['geometry'].bounds)),
               '--tmpdir=' + str(ROOT / '工具/tile-work/nanjing'),
               '--output=' + str(directory / 'nanjing-z16.pmtiles'), '--force=true', '--loginterval=30s']
    with (directory / '制包运行记录.log').open('w', encoding='utf-8') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, cwd=ROOT, check=True)
    print('BUILT 南京单线地铁底图，站点从 z12 起保留。', flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--refresh-manifest', action='store_true', help='重新制包后验证数据边界并更新南京清单')
    parser.add_argument('--build', action='store_true', help='仅重制南京，修正站点在 z12～15 的尺寸过滤')
    args = parser.parse_args()
    directory = ROOT / '城市/320100-南京市'
    if args.build:
        build(directory)
        return
    if args.refresh_manifest:
        refresh_manifest(directory)
        return
    lines_file = directory / '地铁/lines.geojson'
    lines = json.loads(lines_file.read_text(encoding='utf-8'))
    services = json.loads((directory / '地铁/routes.json').read_text(encoding='utf-8'))['routes']
    index = json.loads((ROOT / '原始数据/关系索引.json').read_text(encoding='utf-8'))
    relations = {route['id']: route for route in index['routes']}
    by_line = collections.defaultdict(list)
    needed = set()
    for service in services:
        relation = relations[int(service['route_id'].split('/')[1])]
        ways = [member['ref'] for member in relation['members']
                if member['type'] == 'w' and member['role'] in ('', 'forward', 'backward')]
        by_line[service['line_id']].append((service, ways))
        needed.update(ways)

    class Tracks(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.geometries = {}

        def way(self, way):
            if way.id not in needed:
                return
            tags = dict(way.tags)
            if tags.get('railway') not in ('subway', 'light_rail', 'rail'):
                return
            if tags.get('disused') == 'yes' or tags.get('abandoned') == 'yes':
                return
            coordinates = [(node.lon, node.lat) for node in way.nodes if node.location.valid()]
            if len(coordinates) >= 2:
                self.geometries[way.id] = LineString(coordinates)

    tracks = Tracks()
    tracks.apply_buffer((ROOT / '原始数据/jiangsu.osm.pbf').read_bytes(), 'pbf', locations=True, idx='flex_mem')
    boundary_data = json.loads((directory / 'boundary.geojson').read_text(encoding='utf-8'))
    boundary = union_all([shape(feature['geometry']) for feature in boundary_data['features']])
    replacements, report = {}, []
    for feature in lines['features']:
        props = feature['properties']
        # 先确保覆盖完整站序，再在相同站数的方向中选源轨道齐全、编号稳定的一条。
        candidates = sorted(by_line[props['line_id']], key=lambda item: (
            -len(item[0]['station_ids']), sum(way not in tracks.geometries for way in item[1]), item[0]['route_id']))
        service, ways = candidates[0]
        missing = [way for way in ways if way not in tracks.geometries]
        if missing and props.get('track_completeness') != 'source_partial':
            raise RuntimeError(f"{props['name']}选中方向缺少轨道，停止替换：{missing}")
        # 原始交付已标记部分缺失的线路保留真实缺口，不能凭站点位置补造轨道。
        geometry = line_merge(union_all([tracks.geometries[way] for way in ways if way in tracks.geometries]))
        parts = line_parts(geometry.intersection(boundary))
        if not parts:
            raise RuntimeError(f"{props['name']}裁剪后没有轨道，停止替换")
        geometry = parts[0] if len(parts) == 1 else MultiLineString(parts)
        changed = dict(feature, geometry=mapping(geometry), properties=dict(props,
                       display_geometry='single_direction', display_route_id=service['route_id']))
        replacements[props['line_id']] = changed
        report.append({'line': props['name'], 'ref': props['ref'], 'route': service['route_id'],
                       'station_count': len(service['station_ids']), 'parts': len(parts),
                       'old_parts': len(line_parts(shape(feature['geometry']))),
                       'missing_track_ids': missing,
                       'geometry_sha256': hashlib.sha256(geometry.wkb).hexdigest()})
    print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)
    if not args.apply:
        return

    backup = directory / '备份/地铁单线化前'
    backup.mkdir(parents=True, exist_ok=True)
    for file in [lines_file, directory / 'basemap.geojson', directory / 'manifest.json', *directory.glob('*.pmtiles')]:
        target = backup / file.name
        if not target.exists():
            shutil.copy2(file, target)
    basemap_file = directory / 'basemap.geojson'
    basemap = json.loads(basemap_file.read_text(encoding='utf-8'))
    basemap['features'] = [replacements[feature['properties']['line_id']]
                           if feature['properties']['layer'] == 'metro_line' else feature
                           for feature in basemap['features']]
    lines['features'] = list(replacements.values())
    for file, data in [(lines_file, lines), (basemap_file, basemap)]:
        temporary = file.with_suffix(file.suffix + '.part')
        temporary.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
        temporary.replace(file)
    (directory / '地铁/单线化记录.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('APPLIED 南京：已备份原数据，仅替换 metro_line 几何；站点和线路站序未变。', flush=True)


if __name__ == '__main__':
    main()
