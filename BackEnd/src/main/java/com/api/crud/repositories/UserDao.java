package com.api.crud.repositories;

import com.api.crud.DTO.UserUpdateRequestDTO;
import com.api.crud.models.entity.UserModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserDao {

    void register(UserModel user);

    public List<UserModel> getUserByName(String firstName, String lastName);

    Optional<UserModel> getUserByEmail(String email);

    UserModel updateMyProfile(String email, UserUpdateRequestDTO dto);

    List<UserModel> getUsers();

    public Page<UserModel> getUserAdmin(Pageable pageable, String role);

    void update(UserModel user);

    @Query("SELECT u FROM UserModel u WHERE u.id = :id")
    Optional<UserModel> getUserById(@Param("id") Long id);

    public UserModel updateUserById(UserModel Request, Long id);

    boolean deleteUserById(long id);
}