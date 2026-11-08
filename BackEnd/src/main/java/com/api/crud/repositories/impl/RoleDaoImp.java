package com.api.crud.repositories.impl;

import com.api.crud.models.entity.Role;
import com.api.crud.repositories.RoleDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class RoleDaoImp implements RoleDao {

    private static final Logger logger = LoggerFactory.getLogger(RoleDaoImp.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Role> getRoleByName(String roleName) {
        logger.debug("Executing query to fetch role by name: {}", roleName);
        Role role = null;
        try {
            role =
                    entityManager
                            .createQuery(
                                    "SELECT r FROM Role r WHERE r.nameRole = :roleName", Role.class)
                            .setParameter("roleName", roleName)
                            .getSingleResult();
        } catch (NoResultException e) {
            logger.debug("No role found: {}", e.getMessage());
        }
        return Optional.ofNullable(role);
    }

    @Override
    public Optional<Role> getRoleById(Long id) {
        logger.debug("Executing query to find role with ID: {}", id);
        Optional<Role> response;
        Role role = entityManager.find(Role.class, id);
        response = Optional.ofNullable(role);
        return response;
    }

    @Override
    public void saveRole(Role role) {
        entityManager.persist(role);
        logger.debug("Query Role with ID {} was successfully saved", role.getIdRole());
    }

    @Override
    public List<Role> getRoles() {
        List<Role> response;
        List<Role> roles = entityManager.createQuery("FROM Role", Role.class).getResultList();
        logger.debug(" query Successfully retrieved {} roles from the database", roles.size());
        response = roles;
        return response;
    }

    @Override
    public boolean deleteRoleById(Long id) {
        logger.debug("Executing query Attempting to delete role with ID: {}", id);
        boolean response = false;
        Role role = entityManager.find(Role.class, id);
        if (role != null) {
            entityManager.remove(role);
            response = true;
        }
        return response;
    }
}
