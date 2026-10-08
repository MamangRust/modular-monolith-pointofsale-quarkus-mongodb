package com.sanedge.role.repository;

import com.sanedge.role.entity.UserRole;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class UserRoleRepository implements ReactivePanacheMongoRepository<UserRole> {

    @jakarta.inject.Inject
    RoleRepository roleRepository;

    public Uni<UserRole> assignRole(String userId, String roleId) {
        return roleRepository.findById(new ObjectId(roleId))
                .chain(role -> {
                    if (role == null) {
                        return Uni.createFrom().failure(new jakarta.ws.rs.NotFoundException("Role not found with id: " + roleId));
                    }
                    UserRole userRole = new UserRole(new ObjectId(userId), new ObjectId(roleId));
                    return persist(userRole);
                });
    }

    public Uni<Boolean> removeRole(String userId, String roleId) {
        return delete("{ 'userId': ?1, 'roleId': ?2 }", new ObjectId(userId), new ObjectId(roleId))
                .map(count -> count > 0);
    }
}
