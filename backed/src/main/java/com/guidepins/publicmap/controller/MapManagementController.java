package com.guidepins.publicmap.controller;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.publicmap.domain.ManagedMap;
import com.guidepins.publicmap.service.PublicMapService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 地图管理交互入口；归属、角色和时间均不接受客户端指定。 */
@RestController
public class MapManagementController {
    @Autowired
    private PublicMapService maps;

    @GetMapping("/api/me/maps")
    List<ManagedMap> owned(@RequestParam UUID cityId, @AuthenticationPrincipal AccountIdentity user) {
        return maps.owned(cityId, user);
    }

    @PostMapping("/api/cities/{cityId}/maps")
    @ResponseStatus(HttpStatus.CREATED)
    ManagedMap create(@PathVariable UUID cityId, @Valid @RequestBody CreateMap body,
                      @AuthenticationPrincipal AccountIdentity user) {
        return maps.create(cityId, body.name(), body.visibility(), user);
    }

    @PatchMapping("/api/maps/{mapId}/name")
    ManagedMap rename(@PathVariable UUID mapId, @Valid @RequestBody RenameMap body,
                      @AuthenticationPrincipal AccountIdentity user) {
        return maps.rename(mapId, body.name(), user);
    }

    @PatchMapping("/api/maps/{mapId}/visibility")
    ManagedMap visibility(@PathVariable UUID mapId, @Valid @RequestBody ChangeVisibility body,
                          @AuthenticationPrincipal AccountIdentity user) {
        return maps.changeVisibility(mapId, body.visibility(), user);
    }

    public record CreateMap(@NotBlank @Size(max = 120) String name,
                            @NotBlank @Pattern(regexp = "private|public") String visibility) {
        public CreateMap {
            name = name == null ? null : name.strip();
            visibility = visibility == null ? "private" : visibility;
        }
    }

    public record RenameMap(@NotBlank @Size(max = 120) String name) {
        public RenameMap {
            name = name == null ? null : name.strip();
        }
    }

    public record ChangeVisibility(@NotBlank @Pattern(regexp = "private|public") String visibility) {}
}
