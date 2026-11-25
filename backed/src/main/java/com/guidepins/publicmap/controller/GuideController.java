package com.guidepins.publicmap.controller;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.common.ApiResponse;
import com.guidepins.publicmap.domain.Guide;
import com.guidepins.publicmap.domain.GuideDetail;
import com.guidepins.publicmap.service.GuideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 攻略与出处的读写入口；所属地图来自路径，归属与角色不接受客户端指定。 */
@RestController
public class GuideController {
    @Autowired
    private GuideService guides;

    @GetMapping("/api/maps/{mapId}/guides")
    List<GuideDetail> list(@PathVariable UUID mapId, @AuthenticationPrincipal AccountIdentity viewer) {
        return guides.list(mapId, viewer);
    }

    @GetMapping("/api/maps/{mapId}/guides/{guideId}")
    GuideDetail get(@PathVariable UUID mapId, @PathVariable UUID guideId,
                    @AuthenticationPrincipal AccountIdentity viewer) {
        return guides.get(mapId, guideId, viewer);
    }

    @PostMapping("/api/maps/{mapId}/guides")
    @ResponseStatus(HttpStatus.CREATED)
    GuideDetail create(@PathVariable UUID mapId, @Valid @RequestBody GuideBody body,
                       @AuthenticationPrincipal AccountIdentity user) {
        return guides.create(mapId, body.title(), body.body(), body.sourceUrl(), user);
    }

    @PatchMapping("/api/maps/{mapId}/guides/{guideId}")
    GuideDetail update(@PathVariable UUID mapId, @PathVariable UUID guideId, @Valid @RequestBody GuideBody body,
                       @AuthenticationPrincipal AccountIdentity user) {
        return guides.update(mapId, guideId, body.title(), body.body(), body.sourceUrl(), user);
    }

    @DeleteMapping("/api/maps/{mapId}/guides/{guideId}")
    ApiResponse<Void> delete(@PathVariable UUID mapId, @PathVariable UUID guideId,
                             @AuthenticationPrincipal AccountIdentity user) {
        guides.delete(mapId, guideId, user);
        return ApiResponse.success(200, "攻略已删除", null);
    }

    /** 整体重设关联：请求里的图钉顺序就是站序，空数组表示清空关联（攻略保留）。 */
    @PutMapping("/api/maps/{mapId}/guides/{guideId}/pins")
    GuideDetail setPins(@PathVariable UUID mapId, @PathVariable UUID guideId,
                        @Valid @RequestBody PinOrder body,
                        @AuthenticationPrincipal AccountIdentity user) {
        return guides.setPins(mapId, guideId, body.pinIds(), user);
    }

    /** 反向查询：提到某个图钉的全部攻略，供图钉侧栏展示出处。 */
    @GetMapping("/api/maps/{mapId}/pins/{pinId}/guides")
    List<Guide> ofPin(@PathVariable UUID mapId, @PathVariable UUID pinId,
                      @AuthenticationPrincipal AccountIdentity viewer) {
        return guides.ofPin(mapId, pinId, viewer);
    }

    /** 新建与修改共用同一组字段：修改是整体替换，来源链接为 null 表示没有出处链接。 */
    public record GuideBody(@NotBlank @Size(max = 120) String title,
                            @NotBlank @Size(max = 5000) String body,
                            @Size(max = 2048) String sourceUrl) {
        public GuideBody {
            title = title == null ? null : title.strip();
            body = body == null ? null : body.strip();
            sourceUrl = sourceUrl == null ? null : sourceUrl.strip();
        }
    }

    public record PinOrder(@NotNull List<UUID> pinIds) {}
}
