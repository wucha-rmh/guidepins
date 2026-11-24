package com.guidepins.publicmap.controller;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.common.ApiResponse;
import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.service.PinEditingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 图钉编辑入口；所属地图来自路径，归属地图与角色不接受客户端指定。 */
@RestController
public class MapPinEditingController {
    @Autowired
    private PinEditingService pins;

    @PostMapping("/api/maps/{mapId}/pins")
    @ResponseStatus(HttpStatus.CREATED)
    MapPin create(@PathVariable UUID mapId, @Valid @RequestBody PinBody body,
                  @AuthenticationPrincipal AccountIdentity user) {
        return pins.create(mapId, body.name(), body.description(), body.longitude(), body.latitude(), user);
    }

    @PatchMapping("/api/maps/{mapId}/pins/{pinId}")
    MapPin update(@PathVariable UUID mapId, @PathVariable UUID pinId, @Valid @RequestBody PinBody body,
                  @AuthenticationPrincipal AccountIdentity user) {
        return pins.update(mapId, pinId, body.name(), body.description(), body.longitude(), body.latitude(), user);
    }

    @DeleteMapping("/api/maps/{mapId}/pins/{pinId}")
    ApiResponse<Void> delete(@PathVariable UUID mapId, @PathVariable UUID pinId,
                             @AuthenticationPrincipal AccountIdentity user) {
        pins.delete(mapId, pinId, user);
        return ApiResponse.success(200, "图钉已删除", null);
    }

    /** 新增与修改共用同一组字段：修改是整体替换，说明为 null 表示清空说明。 */
    public record PinBody(@NotBlank @Size(max = 120) String name,
                          @Size(max = 2000) String description,
                          @NotNull Double longitude,
                          @NotNull Double latitude) {
        public PinBody {
            name = name == null ? null : name.strip();
        }
    }
}
