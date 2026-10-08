package com.sanedge.user.repository;

import java.time.Instant;

import com.sanedge.user.domain.requests.FindAllUsers;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.user.entity.User;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

@ApplicationScoped
public class UserRepository implements ReactivePanacheMongoRepository<User> {

    public Uni<PagedResult<User>> findUsers(FindAllUsers req) {
        return findUsersInternal(req, null);
    }

    public Uni<PagedResult<User>> findActiveUsers(FindAllUsers req) {
        return findUsersInternal(req, null);
    }

    public Uni<PagedResult<User>> findTrashedUsers(FindAllUsers req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : null;

        ReactivePanacheQuery<User> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }");
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, '$or': [ { 'firstname': { '$regex': ?1, '$options': 'i' } }, { 'lastname': { '$regex': ?1, '$options': 'i' } }, { 'email': { '$regex': ?1, '$options': 'i' } } ] }", keyword);
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    private Uni<PagedResult<User>> findUsersInternal(FindAllUsers req, Object ignored) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : null;

        ReactivePanacheQuery<User> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }");
        } else {
            panacheQuery = find("{ 'deletedAt': null, '$or': [ { 'firstname': { '$regex': ?1, '$options': 'i' } }, { 'lastname': { '$regex': ?1, '$options': 'i' } }, { 'email': { '$regex': ?1, '$options': 'i' } } ] }", keyword);
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<User> findById(String id) {
        return find("_id", new ObjectId(id)).firstResult();
    }

    public Uni<User> findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public Uni<User> findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public Uni<Boolean> existsByUsername(String username) {
        return count("username = ?1", username).map(c -> c > 0);
    }

    public Uni<Boolean> existsByEmail(String email) {
        return count("email = ?1", email).map(c -> c > 0);
    }

    public Uni<User> trash(String userId) {
        return findById(userId)
                .chain(user -> {
                    if (user != null && user.getDeletedAt() == null) {
                        user.setDeletedAt(Instant.now());
                        return user.update().map(v -> user);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<User> restore(String userId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(userId)).firstResult()
                .chain(user -> {
                    if (user != null) {
                        user.setDeletedAt(null);
                        return user.update().map(v -> user);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<User> deletePermanent(String userId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(userId)).firstResult()
                .chain(user -> {
                    if (user != null) {
                        return user.delete().map(v -> user);
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
