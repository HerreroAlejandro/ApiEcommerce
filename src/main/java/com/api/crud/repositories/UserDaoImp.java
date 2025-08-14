package com.api.crud.repositories;

import com.api.crud.DTO.UserUpdateDTO;
import com.api.crud.models.UserModel;
import com.api.crud.services.EmailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Transactional
public class UserDaoImp implements UserDao {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private EmailService emailService;

    private static final Logger logger = LoggerFactory.getLogger(UserDaoImp.class);


    @Transactional
    public void register(UserModel user) {
        logger.debug("Executing query Registering new user: {}", user.getEmail());
        try {
            entityManager.merge(user);

            String subject = "Registration Confirmation";
            String body = "Hello " + user.getFirstName() + ",\n\nYour account has been created successfully.\n\nGreetings!";
            boolean emailSent = emailService.sendEmail(user.getEmail(), subject, body);

            if (!emailSent) {
                logger.error("Failed to send confirmation email to {}, rolling back registration", user.getEmail());
                throw new RuntimeException("Failed to send confirmation email, User not registered.");
            }
        } catch (Exception e) {
            logger.error("Error occurred while registering user {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to send confirmation email, User not registered.");
        }
    }

    @Override
    public Optional<UserModel> findUserByName(String firstName, String lastName) {
        logger.debug("Executing query Searching for user with name: {} {}", firstName, lastName);
        UserModel user = null;
        try {
            user = entityManager
                    .createQuery("SELECT u FROM UserModel u WHERE u.firstName = :firstName AND u.lastName = :lastName", UserModel.class)
                    .setParameter("firstName", firstName)
                    .setParameter("lastName", lastName)
                    .getSingleResult();
        } catch (NoResultException e) {
            logger.warn("No user found with name: {} {}", firstName, lastName);
        } catch (Exception e) {
            logger.error("Unexpected error while querying searching for user with name {} {}: {}", firstName, lastName, e.getMessage());
        }
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<UserModel> findUserByEmail(String email) {
        logger.debug("Executing query to find user with email: {}", email);
        Optional<UserModel> response;
        try {
            UserModel user = entityManager
                    .createQuery(
                            "SELECT u FROM UserModel u LEFT JOIN FETCH u.roles WHERE u.email = :email",
                            UserModel.class)
                    .setParameter("email", email)
                    .getSingleResult();
            response = Optional.of(user);
        } catch (NoResultException e) {
            logger.warn("No user found with email: {}", email);
            response = Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error while querying user with email {}: {}", email, e.getMessage());
            response = Optional.empty();
        }
        return response;
    }

    @Override
    public UserModel updateUserByEmail(String email, UserUpdateDTO dto) {
        Optional<UserModel> optionalUser = findUserByEmail(email);

        if (optionalUser.isPresent()) {
            UserModel user = optionalUser.get();

            if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
            if (dto.getLastName() != null) user.setLastName(dto.getLastName());
            if (dto.getPhone() != null) user.setPhone(dto.getPhone());

            entityManager.merge(user);
            return user;
        } else {
            throw new RuntimeException("User with email " + email + " not found.");
        }
    }

    @Override
    public boolean deleteUserByEmail(String email) {
        logger.debug("Executing query Attempting to delete user with Email: {}", email);
        boolean response = false;
        try {
            UserModel user = entityManager.createQuery(
                            "SELECT u FROM UserModel u WHERE u.email = :email", UserModel.class)
                    .setParameter("email", email)
                    .getSingleResult();

            if (user != null) {
                entityManager.remove(user);
                response = true;
            }
        } catch (NoResultException e) {
            logger.warn("No user found with email: {}", email);
        } catch (Exception e) {
            logger.error("Error while querying delete user with Email {}: {}", email, e.getMessage(), e);
        }
        return response;
    }

    @Override
    public List<UserModel> getUsers() {
        logger.debug("Executing query to fetch user");
        List<UserModel> users;
        try {
            String query = "FROM UserModel u";
            users = entityManager.createQuery(query, UserModel.class).getResultList();
        } catch (Exception e) {
            logger.error("Error while querying user: {}", e.getMessage());
            users = Collections.emptyList();
        }
        return users;
    }

    @Override
    public Page<UserModel> getUsersModel(Pageable pageable) {
        logger.debug("Executing query to fetch users with pagination: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        String query = "FROM UserModel u ORDER BY u." + pageable.getSort().iterator().next().getProperty();

        List<UserModel> users;
        Long total;
        Page<UserModel> response;
        try {
            users = entityManager.createQuery(query, UserModel.class)
                    .setFirstResult(pageable.getPageNumber() * pageable.getPageSize())
                    .setMaxResults(pageable.getPageSize())
                    .getResultList();
            total = entityManager.createQuery("SELECT COUNT(u) FROM UserModel u", Long.class)
                    .getSingleResult();
            response = new PageImpl<>(users, pageable, total);
        } catch (Exception e) {
            logger.error("Error while querying fetching paginated users: {}", e.getMessage());
            response = Page.empty();
        }
        return response;
    }

    @Override
    @Transactional
    public void update(UserModel user) {
        entityManager.merge(user);
    }

    @Override
    public Optional<UserModel> findUserById(Long id) {
        logger.debug("Executing query to find user with ID: {}", id);
        Optional<UserModel> response;
        try {
            UserModel user = entityManager.find(UserModel.class, id);
            response = Optional.ofNullable(user);
        } catch (Exception e) {
            logger.error("Error while querying user with ID {}: {}", id, e.getMessage());
            response = Optional.empty();
        }
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

    @Override
    public boolean deleteUserById(long id) {
        logger.debug("Executing query Attempting to delete user with ID: {}", id);
        boolean response = false;
        try {
            UserModel user = entityManager.find(UserModel.class, id);
            if (user != null) {
                entityManager.remove(user);
                response = true;
            }
        } catch (Exception e) {
            logger.error("Error while querying delete user with ID {}: {}", id, e.getMessage(), e);
        }
        return response;
    }

}