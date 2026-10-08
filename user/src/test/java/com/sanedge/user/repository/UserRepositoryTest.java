package com.sanedge.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.user.domain.requests.FindAllUsers;
import com.sanedge.user.entity.User;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class UserRepositoryTest extends MongoTestBase {

    @Inject
    UserRepository userRepository;

    private Uni<User> createAndPersistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFirstname("First_" + username);
        user.setLastname("Last_" + username);
        user.setEmail(email);
        user.setPassword("password123");
        return userRepository.persist(user).replaceWith(user);
    }

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll().await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistUser("johndoe", "john.doe@example.com")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
                })
                .chain(saved -> userRepository.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getUsername()).isEqualTo("johndoe");
                    assertThat(found.getFirstname()).isEqualTo("First_johndoe");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByEmail() {
        createAndPersistUser("janedoe", "jane.doe@example.com")
                .chain(ignored -> userRepository.findByEmail("jane.doe@example.com"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getUsername()).isEqualTo("janedoe");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByEmailReturnsNullWhenNotFound() {
        userRepository.findByEmail("nonexistent@example.com")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        userRepository.findById("507f1f77bcf86cd799439012")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByUsername() {
        createAndPersistUser("alice", "alice@example.com")
                .chain(ignored -> userRepository.findByUsername("alice"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getEmail()).isEqualTo("alice@example.com");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testExistsByUsernameAndEmail() {
        createAndPersistUser("bob", "bob@example.com")
                .chain(ignored -> Uni.join().all(
                        userRepository.existsByUsername("bob"),
                        userRepository.existsByEmail("bob@example.com"),
                        userRepository.existsByUsername("notbob"),
                        userRepository.existsByEmail("notbob@example.com"))
                        .andCollectFailures())
                .invoke(results -> {
                    assertThat(results.get(0)).isTrue();
                    assertThat(results.get(1)).isTrue();
                    assertThat(results.get(2)).isFalse();
                    assertThat(results.get(3)).isFalse();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashUser() {
        createAndPersistUser("trashme", "trash@example.com")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> userRepository.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashUserReturnsNullIfAlreadyTrashed() {
        createAndPersistUser("trashme2", "trash2@example.com")
                .chain(saved -> userRepository.trash(saved.id.toHexString())
                        .chain(ignored -> userRepository.trash(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashUserReturnsNullIfNotFound() {
        userRepository.trash("507f1f77bcf86cd799439011")
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreUser() {
        createAndPersistUser("restoreme", "restore@example.com")
                .chain(saved -> userRepository.trash(saved.id.toHexString())
                        .chain(ignored -> userRepository.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreUserReturnsNullIfNotTrashed() {
        createAndPersistUser("restoreme2", "restore2@example.com")
                .chain(saved -> userRepository.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistUser("deleteme", "delete@example.com")
                .chain(saved -> userRepository.trash(saved.id.toHexString())
                        .chain(ignored -> userRepository.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return userRepository.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistUser("deleteme2", "delete2@example.com")
                .chain(saved -> userRepository.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return userRepository.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistUser("bulk1", "bulk1@example.com"),
                        createAndPersistUser("bulk2", "bulk2@example.com"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        userRepository.trash(tuple.getItem1().id.toHexString()),
                        userRepository.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> userRepository.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    userRepository.findById(tuple.getItem1().id.toHexString()),
                                    userRepository.findById(tuple.getItem2().id.toHexString()))
                                    .andCollectFailures()
                                    .replaceWith(tuple);
                        }))
                .invoke(tuple -> {
                    assertThat(tuple.getItem1().getDeletedAt()).isNull();
                    assertThat(tuple.getItem2().getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistUser("bulkdel1", "bulkdel1@example.com"),
                        createAndPersistUser("bulkdel2", "bulkdel2@example.com"),
                        createAndPersistUser("bulkdel3", "bulkdel3@example.com"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        userRepository.trash(tuple.getItem1().id.toHexString()),
                        userRepository.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> userRepository.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    userRepository.findById(tuple.getItem1().id.toHexString()),
                                    userRepository.findById(tuple.getItem2().id.toHexString()),
                                    userRepository.findById(tuple.getItem3().id))
                                    .andCollectFailures();
                        }))
                .invoke(found -> {
                    assertThat(found.get(0)).isNull();
                    assertThat(found.get(1)).isNull();
                    assertThat(found.get(2)).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveUsers() {
        Uni.combine().all()
                .unis(createAndPersistUser("active1", "active1@example.com"),
                        createAndPersistUser("trashed1", "trashed1@example.com"))
                .asTuple()
                .chain(tuple -> userRepository.trash(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllUsers req = new FindAllUsers();
                    req.setPage(1);
                    req.setPageSize(10);
                    return userRepository.findActiveUsers(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getUsername()).isEqualTo("active1");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedUsers() {
        Uni.combine().all()
                .unis(createAndPersistUser("trashed2", "trashed2@example.com"),
                        createAndPersistUser("active2", "active2@example.com"))
                .asTuple()
                .chain(tuple -> userRepository.trash(tuple.getItem1().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllUsers req = new FindAllUsers();
                    req.setPage(1);
                    req.setPageSize(10);
                    return userRepository.findTrashedUsers(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getUsername()).isEqualTo("trashed2");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindUsersWithSearchKeyword() {
        Uni.join().all(
                createAndPersistUser("superman", "superman@dc.com"),
                createAndPersistUser("spiderman", "spiderman@marvel.com"),
                createAndPersistUser("batman", "batman@dc.com"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllUsers req = new FindAllUsers();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("man");
                    return userRepository.findUsers(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(3);
                    assertThat(result.getTotalRecords()).isEqualTo(3);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindUsersWithPagination() {
        Uni.join().all(
                createAndPersistUser("pageuser1", "pageuser1@example.com"),
                createAndPersistUser("pageuser2", "pageuser2@example.com"),
                createAndPersistUser("pageuser3", "pageuser3@example.com"),
                createAndPersistUser("pageuser4", "pageuser4@example.com"),
                createAndPersistUser("pageuser5", "pageuser5@example.com"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllUsers reqPage1 = new FindAllUsers();
                    reqPage1.setPage(1);
                    reqPage1.setPageSize(2);
                    return userRepository.findUsers(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllUsers reqPage2 = new FindAllUsers();
                    reqPage2.setPage(2);
                    reqPage2.setPageSize(2);
                    return userRepository.findUsers(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                                assertThat(page2.getData().get(0).getUsername()).isNotIn(
                                        page1.getData().get(0).getUsername(),
                                        page1.getData().get(1).getUsername());
                            });
                })
                .replaceWithVoid().await().indefinitely();
    }
}
