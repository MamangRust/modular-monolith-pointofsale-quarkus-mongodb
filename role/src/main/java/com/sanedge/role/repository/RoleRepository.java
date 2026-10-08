package com.sanedge.role.repository;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.role.domain.requests.FindAllRoles;
import com.sanedge.role.entity.Role;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class RoleRepository implements ReactivePanacheMongoRepository<Role> {

    public Uni<PagedResult<Role>> findRoles(FindAllRoles req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Role> panacheQuery;
        if (keyword == null) {
            panacheQuery = findAll();
        } else {
            panacheQuery = find("{ 'roleName': { '$regex': ?1, '$options': 'i' } }", Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Role>> findActiveRoles(FindAllRoles req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Role> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }");
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'roleName': { '$regex': ?1, '$options': 'i' } }", Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Role>> findTrashedRoles(FindAllRoles req) {
        int pageIndex = req.getPage() > 0 ? req.getPage() - 1 : 0;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Role> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }");
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, 'roleName': { '$regex': ?1, '$options': 'i' } }", Pattern.quote(keyword));
        }
        panacheQuery.page(pageIndex, req.getPageSize());

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Role> findById(String roleId) {
        return find("_id", new ObjectId(roleId)).firstResult();
    }

    public Uni<Role> findByRoleName(String roleName) {
        return find("roleName", roleName).firstResult();
    }

    public Uni<List<Role>> findUserRoles(String userId) {
        Uni<List<com.sanedge.role.entity.UserRole>> userRolesUni =
                com.sanedge.role.entity.UserRole.<com.sanedge.role.entity.UserRole>list("{ 'userId': ?1 }",
                        new ObjectId(userId));
        return userRolesUni.chain(urs -> {
            List<ObjectId> roleIds = new java.util.ArrayList<>();
            for (com.sanedge.role.entity.UserRole ur : urs) {
                roleIds.add(ur.getRoleId());
            }
            return find("{ 'deletedAt': null, '_id': { '$in': ?1 } }", Sort.ascending("createdAt"), roleIds)
                    .list();
        });
    }

    public Uni<Role> trash(String roleId) {
        return find("_id", new ObjectId(roleId)).firstResult()
                .chain(role -> {
                    if (role != null && role.getDeletedAt() == null) {
                        role.setDeletedAt(Instant.now());
                        return role.update().map(v -> role);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Role> restore(String roleId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(roleId)).firstResult()
                .chain(role -> {
                    if (role != null) {
                        role.setDeletedAt(null);
                        return role.update().map(v -> role);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Role> deletePermanent(String roleId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(roleId)).firstResult()
                .chain(role -> {
                    if (role != null) {
                        return role.delete().map(v -> role);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Boolean> restoreAllDeleted() {
        return update("{ '$set': { 'deletedAt': null } }")
                .where("{ 'deletedAt': { '$ne': null } }")
                .map(updatedCount -> updatedCount > 0);
    }

    public Uni<Boolean> deleteAllDeleted() {
        return delete("{ 'deletedAt': { '$ne': null } }")
                .map(deletedCount -> deletedCount > 0);
    }
}
