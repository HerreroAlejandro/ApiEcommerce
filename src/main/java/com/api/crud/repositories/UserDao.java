package com.api.crud.repositories;

import com.api.crud.DTO.UserUpdateDTO;
import com.api.crud.models.UserModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserDao {

    void register(UserModel user);

    public Optional<UserModel> findUserByName(String firstName, String lastName);

    Optional<UserModel> findUserByEmail(String email);

    UserModel updateUserByEmail(String email, UserUpdateDTO dto);

    boolean deleteUserByEmail(String email);

    List<UserModel> getUsers();

    Page<UserModel> getUsersModel(Pageable pageable);

    void update(UserModel user);

    @Query("SELECT u FROM UserModel u WHERE u.id = :id")
    Optional<UserModel> findUserById(@Param("id") Long id);

    public UserModel updateUserById(UserModel Request, Long id);

    boolean deleteUserById(long id);
}