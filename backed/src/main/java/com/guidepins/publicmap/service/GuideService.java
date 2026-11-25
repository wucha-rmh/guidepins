package com.guidepins.publicmap.service;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.exception.ApiException;
import com.guidepins.exception.ResourceNotFoundException;
import com.guidepins.publicmap.domain.Guide;
import com.guidepins.publicmap.domain.GuideDetail;
import com.guidepins.publicmap.domain.GuidePinLink;
import com.guidepins.publicmap.mapper.GuideDataMapper;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 攻略业务：校验字段，把写入结果翻译成 403 或 404，并维护攻略与图钉的站序关联。
 * 权限判断留在 SQL 里，这里只负责解释「写不进去」的原因。
 */
@Service
public class GuideService {
    private static final int TITLE_LIMIT = 120;
    private static final int BODY_LIMIT = 5000;
    private static final int URL_LIMIT = 2048;

    @Autowired
    private GuideDataMapper guides;

    @Autowired
    private PublicMapDataMapper maps;

    /** 地图下的攻略列表；每篇带上按站序排列的图钉编号，前端一次请求就能渲染。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<GuideDetail> list(UUID mapId, AccountIdentity viewer) {
        UUID viewerId = viewer == null ? null : viewer.id();
        readable(mapId, viewerId);
        Map<UUID, List<GuidePinLink>> links = guides.findLinksByMap(mapId, viewerId);
        return guides.findByMapId(mapId, viewerId).stream()
                .map(guide -> toDetail(guide, links.getOrDefault(guide.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GuideDetail get(UUID mapId, UUID guideId, AccountIdentity viewer) {
        UUID viewerId = viewer == null ? null : viewer.id();
        readable(mapId, viewerId);
        Guide guide = guides.findById(mapId, guideId, viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Guide not found"));
        return toDetail(guide, guides.findLinks(mapId, guideId, viewerId));
    }

    /** 新建攻略：允许先只写正文、暂不关联图钉，草稿状态由前端自行标识。 */
    @Transactional
    public GuideDetail create(UUID mapId, String title, String body, String sourceUrl, AccountIdentity user) {
        requireUser(user);
        String guideTitle = requireTitle(title);
        String guideBody = requireBody(body);
        String link = normalizeSourceUrl(sourceUrl);
        Guide created = guides.create(mapId, user.id(), guideTitle, guideBody, link)
                .orElseGet(() -> {
                    // 地图不可见时返回 404；可见但无写权限时返回 403，与地图改名保持一致。
                    readable(mapId, user.id());
                    throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该地图的权限");
                });
        return toDetail(created, List.of());
    }

    /** 修改攻略：标题、正文、来源链接整体替换，内容没有实际变化时不推进攻略的更新时间。 */
    @Transactional
    public GuideDetail update(UUID mapId, UUID guideId, String title, String body, String sourceUrl,
                              AccountIdentity user) {
        requireUser(user);
        String guideTitle = requireTitle(title);
        String guideBody = requireBody(body);
        String link = normalizeSourceUrl(sourceUrl);
        Guide updated = guides.update(mapId, guideId, user.id(), guideTitle, guideBody, link)
                .orElseGet(() -> {
                    readable(mapId, user.id());
                    if (guides.findById(mapId, guideId, user.id()).isEmpty()) {
                        throw new ResourceNotFoundException("Guide not found");
                    }
                    throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该攻略的权限");
                });
        return toDetail(updated, guides.findLinks(mapId, guideId, user.id()));
    }

    /** 删除攻略：关联行随外键级联删除，图钉本身保留。 */
    @Transactional
    public void delete(UUID mapId, UUID guideId, AccountIdentity user) {
        requireUser(user);
        if (!guides.delete(mapId, guideId, user.id())) {
            readable(mapId, user.id());
            if (guides.findById(mapId, guideId, user.id()).isEmpty()) {
                throw new ResourceNotFoundException("Guide not found");
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该攻略的权限");
        }
    }

    /** 整体重设关联与站序：请求里的数组顺序就是站序，空数组表示清空关联。 */
    @Transactional
    public GuideDetail setPins(UUID mapId, UUID guideId, List<UUID> pinIds, AccountIdentity user) {
        requireUser(user);
        List<UUID> ordered = distinct(pinIds);
        Guide guide = guides.findWritable(mapId, guideId, user.id())
                .orElseGet(() -> {
                    readable(mapId, user.id());
                    if (guides.findById(mapId, guideId, user.id()).isEmpty()) {
                        throw new ResourceNotFoundException("Guide not found");
                    }
                    throw new ApiException(HttpStatus.FORBIDDEN, "没有修改该攻略的权限");
                });
        List<GuidePinLink> current = guides.findLinks(mapId, guideId, user.id());
        if (!ordered.isEmpty()) {
            // 逐个校验：图钉必须属于这张地图，且当前账号对它所在的地图有写权限。
            List<UUID> linkable = guides.findLinkablePinIds(mapId, guideId, user.id(), ordered);
            if (linkable.size() != ordered.size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "关联的图钉必须属于当前地图");
            }
        }
        if (!toLinks(ordered).equals(current)) {
            guides.replaceLinks(mapId, guideId, user.id(), ordered);
        }
        return toDetail(guide, guides.findLinks(mapId, guideId, user.id()));
    }

    /** 反查：提到某个图钉的全部攻略，供图钉侧栏展示出处。 */
    @Transactional(readOnly = true)
    public List<Guide> ofPin(UUID mapId, UUID pinId, AccountIdentity viewer) {
        UUID viewerId = viewer == null ? null : viewer.id();
        readable(mapId, viewerId);
        if (maps.findPin(mapId, pinId, viewerId).isEmpty()) {
            throw new ResourceNotFoundException("Pin not found");
        }
        return guides.findByPin(mapId, pinId, viewerId);
    }

    private GuideDetail toDetail(Guide guide, List<GuidePinLink> pins) {
        return new GuideDetail(guide.id(), guide.mapId(), guide.origin(), guide.title(), guide.body(),
                guide.sourceUrl(), guide.createdAt(), guide.updatedAt(), pins);
    }

    private List<GuidePinLink> toLinks(List<UUID> ordered) {
        List<GuidePinLink> links = new ArrayList<>(ordered.size());
        for (int position = 0; position < ordered.size(); position++) {
            links.add(new GuidePinLink(ordered.get(position), position));
        }
        return links;
    }

    /** 站序不允许重复，重复直接按参数错误处理，避免唯一约束在写入中途报错。 */
    private List<UUID> distinct(List<UUID> pinIds) {
        List<UUID> ordered = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        if (pinIds == null) {
            return ordered;
        }
        for (UUID pinId : pinIds) {
            if (pinId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "图钉编号不能为空");
            }
            if (!seen.add(pinId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一条图钉不能重复关联");
            }
            ordered.add(pinId);
        }
        return ordered;
    }

    private void requireUser(AccountIdentity user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
    }

    private String requireTitle(String title) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "攻略标题不能为空");
        }
        if (value.length() > TITLE_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "攻略标题不能超过 " + TITLE_LIMIT + " 个字符");
        }
        return value;
    }

    private String requireBody(String body) {
        String value = body == null ? "" : body.strip();
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "攻略正文不能为空");
        }
        if (value.length() > BODY_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "攻略正文不能超过 " + BODY_LIMIT + " 个字符");
        }
        return value;
    }

    /** 来源链接可选；只接受 http 或 https，且不允许空白字符，与数据库约束保持一致。 */
    private String normalizeSourceUrl(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }
        String value = sourceUrl.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > URL_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "来源链接不能超过 " + URL_LIMIT + " 个字符");
        }
        boolean http = value.startsWith("http://");
        boolean https = value.startsWith("https://");
        if (!http && !https) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "来源链接需以 http:// 或 https:// 开头");
        }
        // 协议后面必须至少有一个非空白字符，否则数据库的检查约束会拒绝。
        if (value.length() <= (http ? 7 : 8)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "来源链接缺少地址");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "来源链接不能包含空白字符");
            }
        }
        return value;
    }

    private void readable(UUID mapId, UUID viewerId) {
        maps.findById(mapId, viewerId).orElseThrow(() -> new ResourceNotFoundException("Map not found"));
    }
}
