package com.sanedge.role.service;

import com.sanedge.role.domain.requests.CreateRoleRequest;
import com.sanedge.role.domain.requests.UpdateRoleRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.role.domain.response.RoleResponse;
import com.sanedge.role.domain.response.RoleResponseDeleteAt;
import com.sanedge.role.domain.response.UserRoleResponse;

import io.smallrye.mutiny.Uni;

public interface RoleCommandService {
    Uni<ApiResponse<RoleResponse>> create(CreateRoleRequest request);
    Uni<ApiResponse<RoleResponse>> update(UpdateRoleRequest request);
    Uni<ApiResponse<RoleResponseDeleteAt>> trash(String id);
    Uni<ApiResponse<RoleResponseDeleteAt>> restore(String id);
    Uni<ApiResponse<Void>> deletePermanent(String id);
    Uni<ApiResponse<Void>> restoreAllTrashedRoles();
    Uni<ApiResponse<Void>> deleteAllTrashedRoles();
    Uni<ApiResponse<UserRoleResponse>> assignRoleToUser(String userId, String roleId);
    Uni<ApiResponse<Void>> removeRoleFromUser(String userId, String roleId);
}
