package com.api.crud.repositories;

import com.api.crud.models.entity.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class RoleDaoImp implements RoleDao {
    @PersistenceContext
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(RoleDaoImp.class);

    @Override
    public Optional<Role> findRoleByName(String roleName) {
        logger.debug("Executing query to fetch role by name: {}", roleName);
        Role role = null;
        try {
            role = entityManager.createQuery(
                            "SELECT r FROM Role r WHERE r.nameRole = :roleName", Role.class)
                    .setParameter("roleName", roleName)
                    .getSingleResult();
        } catch (NoResultException e) {
            logger.error("Error while querying searching role: {}", e.getMessage());
        }
        return Optional.ofNullable(role);
    }

    @Override
    public Optional<Role> findRoleById(Long id) {
        logger.debug("Executing query to find role with ID: {}", id);
        Optional<Role> response;
        try {
            Role role = entityManager.find(Role.class, id);
            response =  Optional.ofNullable(role);
        } catch (Exception e) {
            logger.error("Error while querying role with ID {}: {}", id, e.getMessage());
            response = Optional.empty();
        }
        return response;
    }

    @Override
    public void saveRole(Role role) {
        try {
            entityManager.persist(role);
            logger.debug("Query Role with ID {} was successfully saved", role.getIdRole());
        } catch (Exception e) {
            logger.error("Error query saving Role with ID {}: {}", role.getIdRole(), e.getMessage());
        }
    }

    @Override
    public List<Role> getRoles() {
        List<Role> response;
        try {
            List<Role> roles = entityManager.createQuery("FROM Role", Role.class).getResultList();
            logger.debug(" query Successfully retrieved {} roles from the database", roles.size());
            response = roles;
        } catch (Exception e) {
            logger.error("Error query retrieving roles from the database: {}", e.getMessage());
            response = Collections.emptyList();
        }
        return response;
    }

    @Override
    public boolean deleteRoleById(Long id) {
        logger.debug("Executing query Attempting to delete role with ID: {}", id);
        boolean response =false;
        try{
            Role role = entityManager.find(Role.class, id);
            if (role != null) {
                entityManager.remove(role);
                response = true;
            }}catch (Exception e) {
            logger.error("Error while querying delete role with ID {}: {}", id, e.getMessage(), e);
        }
        return response;
    }

}
