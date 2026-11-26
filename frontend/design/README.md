# 地铁站图标

用户于 2026-11-26 确认采用蓝灰车头版本。使用内置 `image_gen` 生成，没有使用 CLI/API 回退。

- 原始生成图：[metro-station-generated.png](metro-station-generated.png)，1254 × 1254，保留供追溯。
- 地图纹理：[metro-station.png](../src/assets/metro-station.png)，64 × 64 PNG，6,896 字节。
- 纹理仅取原图 `(193,194,868,868)` 的图标区域，绘制到 64 × 64 画布，圆角半径 11px，去掉预览留白；保留白色底板、车窗与蓝灰边框，没有重新设计或换用字母图标。
- 网站与离线文件共用纹理并内联加载。以 `pixelRatio: 2` 注册，z12 约 18px、z14 约 22px、z16 后约 24px。

最终采用的生成提示词：

```text
Use case: logo-brand. Design a NEW minimal subway station map icon preview, completely different from a green M logo. One icon only, centered on a uniform warm off-white background #F8F7F2. A small white rounded-square tile with a thin dark slate-blue border (#34445D), moderate corner radius about 20 percent. Inside, one exceptionally simple dark slate-blue pictogram of a subway train seen from the front: a compact rounded rectangular train body outline, one broad horizontal windshield near the top, two small round headlights below, and two short diagonal rail strokes extending from the bottom. No wheels, no bus elements, no extra details. Bold consistent monoline strokes, strong silhouette and generous white internal negative space, designed to remain recognizable at 20 to 24 pixels. The icon occupies about 65 percent of the square preview canvas. Strict flat 2D geometric vector-like design with smooth crisp edges, solid uniform colors, no texture or gradients, no shadows or glow, no 3D or perspective. Colors limited to white, dark slate blue and the warm off-white preview background. Absolutely no green, no M, no letters, no words, no captions, no numbers, no watermark. No map pin shape, no mockup, no multiple variants, no presentation board. Prioritize a restrained, simple transit pictogram and uncluttered composition.
```
