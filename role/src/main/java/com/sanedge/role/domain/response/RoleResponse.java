package com.sanedge.role.domain.response;

import com.sanedge.role.entity.Role;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@RegisterForReflection
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleResponse {
    private String id;
    private String name;
    private String createdAt;
    private String updatedAt;

    public static RoleResponse from(Role role) {
        if (role == null) {
            return null;
        }
        return RoleResponse.builder()
                .id(role.id != null ? role.id.toHexString() : null)
                .name(role.getRoleName())
                .createdAt(role.getCreatedAt() != null ? role.getCreatedAt().toString() : null)
                .updatedAt(role.getUpdatedAt() != null ? role.getUpdatedAt().toString() : null)
                .build();
    }
}
