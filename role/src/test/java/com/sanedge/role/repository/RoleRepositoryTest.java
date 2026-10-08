package com.sanedge.role.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.role.domain.requests.FindAllRoles;
import com.sanedge.role.entity.Role;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class RoleRepositoryTest extends MongoTestBase {

    @Inject
    RoleRepository roleRepository;

    private Uni<Role> createAndPersistRole(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        return roleRepository.persist(role).replaceWith(role);
    }

    @BeforeEach
    void cleanDb() {
        roleRepository.deleteAll().await().indefinitely();
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistRole("Admin")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getRoleName()).isEqualTo("Admin");
                })
                .chain(saved -> roleRepository.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getRoleName()).isEqualTo("Admin");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByRoleName() {
        createAndPersistRole("Editor")
                .chain(ignored -> roleRepository.findByRoleName("Editor"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getRoleName()).isEqualTo("Editor");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByRoleNameReturnsNullWhenNotFound() {
        roleRepository.findByRoleName("NonExistentRole")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        roleRepository.findById("507f1f77bcf86cd799439011")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindUserRolesReturnsEmptyWhenNoMapping() {
        roleRepository.findUserRoles("507f1f77bcf86cd799439012")
                .invoke(result -> assertThat(result).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashRole() {
        createAndPersistRole("TrashMe")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> roleRepository.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashRoleReturnsNullIfAlreadyTrashed() {
        createAndPersistRole("TrashMe2")
                .chain(saved -> roleRepository.trash(saved.id.toHexString())
                        .chain(ignored -> roleRepository.trash(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashRoleReturnsNullIfNotFound() {
        roleRepository.trash("507f1f77bcf86cd799439012")
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreRole() {
        createAndPersistRole("RestoreMe")
                .chain(saved -> roleRepository.trash(saved.id.toHexString())
                        .chain(ignored -> roleRepository.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreRoleReturnsNullIfNotTrashed() {
        createAndPersistRole("RestoreMe2")
                .chain(saved -> roleRepository.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistRole("DeleteMe")
                .chain(saved -> roleRepository.trash(saved.id.toHexString())
                        .chain(ignored -> roleRepository.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return roleRepository.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistRole("DeleteMe2")
                .chain(saved -> roleRepository.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return roleRepository.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistRole("BulkRole1"),
                        createAndPersistRole("BulkRole2"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        roleRepository.trash(tuple.getItem1().id.toHexString()),
                        roleRepository.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> roleRepository.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    roleRepository.findById(tuple.getItem1().id.toHexString()),
                                    roleRepository.findById(tuple.getItem2().id.toHexString()))
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
                .unis(createAndPersistRole("BulkDelRole1"),
                        createAndPersistRole("BulkDelRole2"),
                        createAndPersistRole("BulkDelRole3"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        roleRepository.trash(tuple.getItem1().id.toHexString()),
                        roleRepository.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> roleRepository.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    roleRepository.findById(tuple.getItem1().id.toHexString()),
                                    roleRepository.findById(tuple.getItem2().id.toHexString()),
                                    roleRepository.findById(tuple.getItem3().id))
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
    void testFindActiveRoles() {
        Uni.combine().all()
                .unis(createAndPersistRole("ActiveRole1"),
                        createAndPersistRole("TrashedRole1"))
                .asTuple()
                .chain(tuple -> roleRepository.trash(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllRoles req = new FindAllRoles();
                    req.setPage(1);
                    req.setPageSize(10);
                    return roleRepository.findActiveRoles(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getRoleName()).isEqualTo("ActiveRole1");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedRoles() {
        Uni.combine().all()
                .unis(createAndPersistRole("TrashedRole2"),
                        createAndPersistRole("ActiveRole2"))
                .asTuple()
                .chain(tuple -> roleRepository.trash(tuple.getItem1().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllRoles req = new FindAllRoles();
                    req.setPage(1);
                    req.setPageSize(10);
                    return roleRepository.findTrashedRoles(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getRoleName()).isEqualTo("TrashedRole2");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindRolesWithSearchKeyword() {
        Uni.join().all(
                createAndPersistRole("SuperAdmin"),
                createAndPersistRole("SuperEditor"),
                createAndPersistRole("Viewer"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllRoles req = new FindAllRoles();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Super");
                    return roleRepository.findRoles(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindRolesWithPagination() {
        Uni.join().all(
                createAndPersistRole("PageRole1"),
                createAndPersistRole("PageRole2"),
                createAndPersistRole("PageRole3"),
                createAndPersistRole("PageRole4"),
                createAndPersistRole("PageRole5"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllRoles reqPage1 = new FindAllRoles();
                    reqPage1.setPage(1);
                    reqPage1.setPageSize(2);
                    return roleRepository.findRoles(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllRoles reqPage2 = new FindAllRoles();
                    reqPage2.setPage(2);
                    reqPage2.setPageSize(2);
                    return roleRepository.findRoles(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                            });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindRolesWithNullSearchReturnsAll() {
        Uni.join().all(
                createAndPersistRole("RoleC"),
                createAndPersistRole("RoleD"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllRoles req = new FindAllRoles();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch(null);
                    return roleRepository.findRoles(req);
                })
                .invoke(result -> assertThat(result.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindRolesSearchCaseInsensitive() {
        createAndPersistRole("ManagerRole")
                .chain(ignored -> {
                    FindAllRoles reqLower = new FindAllRoles();
                    reqLower.setPage(1);
                    reqLower.setPageSize(10);
                    reqLower.setSearch("manager");
                    return roleRepository.findRoles(reqLower);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getRoleName()).isEqualTo("ManagerRole");
                })
                .replaceWithVoid().await().indefinitely();
    }
}
