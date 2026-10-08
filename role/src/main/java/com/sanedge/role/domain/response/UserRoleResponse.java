package com.sanedge.role.domain.response;

import com.sanedge.role.entity.UserRole;
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
public class UserRoleResponse {
    private String userId;
    private String roleId;
    private String roleName;

    public static UserRoleResponse from(UserRole userRole) {
        if (userRole == null) {
            return null;
        }
        return UserRoleResponse.builder()
                .userId(userRole.getUserId() != null ? userRole.getUserId().toHexString() : null)
                .roleId(userRole.getRoleId() != null ? userRole.getRoleId().toHexString() : null)
                .build();
    }
}
