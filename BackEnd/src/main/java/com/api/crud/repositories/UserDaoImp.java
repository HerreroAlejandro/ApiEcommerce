package com.api.crud.repositories;

import com.api.crud.DTO.UserUpdateRequestDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.UserModel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@Transactional
public class UserDaoImp implements UserDao {

    private static final Logger logger = LoggerFactory.getLogger(UserDaoImp.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void register(UserModel user) {
        entityManager.persist(user);
        entityManager.flush();
    }

    @Override
    public List<UserModel> getUserByName(String firstName, String lastName) {
        logger.debug("Executing query searching for users with name: {} {}", firstName, lastName);
        List<UserModel> users;
        users =
                entityManager
                        .createQuery(
                                "SELECT u FROM UserModel u "
                                        + "WHERE u.firstName = :firstName "
                                        + "AND u.lastName = :lastName",
                                UserModel.class)
                        .setParameter("firstName", firstName)
                        .setParameter("lastName", lastName)
                        .getResultList();
        return users;
    }

    @Override
    public Optional<UserModel> getUserByEmail(String email) {
        logger.debug("Executing query to find user with email: {}", email);
        Optional<UserModel> response;
        try {
            UserModel user =
                    entityManager
                            .createQuery(
                                    "SELECT u FROM UserModel u LEFT JOIN FETCH u.roles WHERE u.email = :email",
                                    UserModel.class)
                            .setParameter("email", email)
                            .getSingleResult();
            response = Optional.of(user);
        } catch (NoResultException e) {
            logger.warn("No user found with email: {}", email);
            response = Optional.empty();
        }
        return response;
    }

    @Override
    public UserModel updateMyProfile(String email, UserUpdateRequestDTO dto) {
        Optional<UserModel> optionalUser = getUserByEmail(email);
        if (optionalUser.isPresent()) {
            UserModel user = optionalUser.get();
            if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
            if (dto.getLastName() != null) user.setLastName(dto.getLastName());
            if (dto.getPhone() != null) user.setPhone(dto.getPhone());
            entityManager.merge(user);
            return user;
        } else {
            throw new ResourceNotFoundException("User with email " + email + " not found.");
        }
    }

    @Override
    public List<UserModel> getUsers() {
        logger.debug("Executing query to fetch user");
        List<UserModel> users;
        String query = "FROM UserModel u";
        users = entityManager.createQuery(query, UserModel.class).getResultList();
        return users;
    }

    @Override
    public Page<UserModel> getUserAdmin(Pageable pageable, String role) {
        logger.debug(
                "Executing query to fetch users with pagination: page={}, size={}, role={}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                role);
        Sort.Order order =
                pageable.getSort().isSorted()
                        ? pageable.getSort().iterator().next()
                        : Sort.Order.asc("id");
        String property = order.getProperty();
        String direction = order.isDescending() ? "DESC" : "ASC";
        Set<String> allowedProperties =
                Set.of("id", "firstName", "lastName", "email", "phone", "status");
        if (!allowedProperties.contains(property)) {
            throw new InvalidRequestException("Invalid sort property: " + property);
        }
        boolean filterByRole = role != null && !role.isBlank();
        String query;
        if (filterByRole) {
            query =
                    "SELECT DISTINCT u "
                            + "FROM UserModel u "
                            + "JOIN u.roles r "
                            + "WHERE r.nameRole = :role "
                            + "ORDER BY u."
                            + property
                            + " "
                            + direction;
        } else {
            query = "FROM UserModel u " + "ORDER BY u." + property + " " + direction;
        }
        TypedQuery<UserModel> userQuery = entityManager.createQuery(query, UserModel.class);
        if (filterByRole) {
            userQuery.setParameter("role", role);
        }
        List<UserModel> users =
                userQuery
                        .setFirstResult(pageable.getPageNumber() * pageable.getPageSize())
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
        String countQuery;
        if (filterByRole) {
            countQuery =
                    "SELECT COUNT(DISTINCT u) "
                            + "FROM UserModel u "
                            + "JOIN u.roles r "
                            + "WHERE r.nameRole = :role";
        } else {
            countQuery = "SELECT COUNT(u) " + "FROM UserModel u";
        }
        TypedQuery<Long> totalQuery = entityManager.createQuery(countQuery, Long.class);
        if (filterByRole) {
            totalQuery.setParameter("role", role);
        }
        Long total = totalQuery.getSingleResult();
        return new PageImpl<>(users, pageable, total);
    }

    @Override
    @Transactional
    public void update(UserModel user) {
        entityManager.merge(user);
    }

    @Override
    public Optional<UserModel> getUserById(Long id) {
        logger.debug("Executing query to find user with ID: {}", id);
        Optional<UserModel> response;
        UserModel user = entityManager.find(UserModel.class, id);
        response = Optional.ofNullable(user);
        return response;
    }

    @Override
    public UserModel updateUserById(UserModel request, Long id) {
        logger.debug("Executing query Updating user with ID: {}", id);
        UserModel user = entityManager.find(UserModel.class, id);
        if (user != null) {
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            user.setPassword(request.getPassword());
            entityManager.merge(user);
        } else {
            logger.warn("Querying User with ID {} wasn't found for update", id);
        }
        return user;
    }
}
