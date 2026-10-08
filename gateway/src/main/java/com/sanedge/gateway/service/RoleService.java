package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.RoleDto;
import com.sanedge.gateway.domain.requests.FindAllRolesRequest;
import io.smallrye.mutiny.Uni;

public interface RoleService {
    Uni<RoleDto.ApiResponsePaginationRole> listRoles(FindAllRolesRequest request);
    Uni<RoleDto.ApiResponseRole> getRole(String id);
    Uni<RoleDto.ApiResponsePaginationRoleDeleteAt> getActiveRoles(FindAllRolesRequest request);
    Uni<RoleDto.ApiResponsePaginationRoleDeleteAt> getTrashedRoles(FindAllRolesRequest request);
    Uni<RoleDto.ApiResponseRole> createRole(RoleDto.CreateRequest body);
    Uni<RoleDto.ApiResponseRole> updateRole(String id, RoleDto.UpdateRequest body);
    Uni<RoleDto.ApiResponseRoleDeleteAt> deleteRole(String id);
    Uni<RoleDto.ApiResponseRoleDeleteAt> restoreRole(String id);
    Uni<RoleDto.SimpleResponse> deleteRolePermanent(String id);
    Uni<RoleDto.SimpleResponse> restoreAllRole();
    Uni<RoleDto.SimpleResponse> deleteAllRolePermanent();
}
