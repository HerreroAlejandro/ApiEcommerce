package com.api.crud.repositories;

import com.api.crud.models.entity.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RoleDao {

    @Query("SELECT r FROM Role r WHERE r.nameRole = :roleName")
    Optional<Role> findRoleByName(@Param("roleName") String roleName);

    Optional<Role> findRoleById(Long id);

    void saveRole(Role role);

    List<Role> getRoles();

    boolean deleteRoleById(Long id);

}