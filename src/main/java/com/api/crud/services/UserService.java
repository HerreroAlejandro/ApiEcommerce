package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.config.JWTUtil;
import com.api.crud.models.Role;
import com.api.crud.models.UserModel;
import com.api.crud.repositories.RoleDao;
import com.api.crud.repositories.UserDao;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);


    public String login(LoginRequestDTO loginRequestDTO) {
        logger.info("Attempting Service login for user: {}", loginRequestDTO.getEmail());
        Optional<UserModelDTO> userModelDTO = findUserByEmail(loginRequestDTO.getEmail());

        if (userModelDTO.isEmpty()) {
            logger.debug("Login failed: User {} not found", loginRequestDTO.getEmail());
            throw new IllegalArgumentException("User not found");
        }

        UserModelDTO user = userModelDTO.get();

        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            logger.debug("Login failed: Incorrect password for {}", loginRequestDTO.getEmail());
            throw new IllegalArgumentException("Wrong password");
        }

        List<String> roles = getRolesForEmail(user.getEmail());
        logger.info("User {} logged in successfully with roles {}", loginRequestDTO.getEmail(), roles);
        return jwtUtil.generateToken(user.getEmail(), roles);
    }

    private List<String> getRolesForEmail(String email) {
        logger.info("Starting to process fetching roles for user with email: {}", email);
        Optional<UserModel> response = userDao.findUserByEmail(email);

        if (response.isPresent()) {
            List<String> roles = response.get().getRoles().stream()
                    .map(Role::getNameRole)
                    .collect(Collectors.toList());
            logger.info("Roles found for user {}: {}", email, roles);
            return roles;
        } else {
            logger.debug("User with email {} not found", email);
            throw new RuntimeException("User not found");
        }
    }

    public void register(UserModelDTO userModelDto) {
        logger.info("Starting to process register users in service");

        UserModel userModel = modelMapper.map(userModelDto, UserModel.class);

        userModel.setPassword(passwordEncoder.encode(userModel.getPassword()));

        if (userModel.getRoles() == null) {
            userModel.setRoles(new HashSet<>());
        }

        Role role = roleDao.findRoleByName("CLIENT")
                .orElseThrow(() -> {
                    logger.debug("Registration failed: Role CLIENT does not exist");
                    return new IllegalArgumentException("El rol CLIENT no existe en la base de datos");
                });
        userModel.getRoles().add(role);
        userModel.setActive(true);

        if (userModel.getOrders() == null) {
            userModel.setOrders(new ArrayList<>());
        }
        userDao.register(userModel);
        logger.info("User {} registered successfully", userModelDto.getEmail());
    }

    public Optional<UserModelDTO> findUserByName(String firstName, String lastName) {
        logger.info("Starting to process search for user with name: {} and lastName: {}", firstName, lastName);

        Optional<UserModel> user = userDao.findUserByName(firstName, lastName);

        Optional<UserModelDTO> response = user.map(userModel -> modelMapper.map(userModel, UserModelDTO.class));

        if (response.isEmpty()) {
            logger.debug("User not found with name: {} and lastName: {}", firstName, lastName);
        }

        return response;
    }

    public Optional<UserModelDTO> findUserByEmail(String email) {
        logger.info("Starting to process search for user with email: {}", email);

        Optional<UserModelDTO> userModelDTO = userDao.findUserByEmail(email)
                .map(user -> modelMapper.map(user, UserModelDTO.class));

        if (userModelDTO.isEmpty()) {
            logger.debug("User with email {} not found", email);
        }

        return userModelDTO;
    }

    public UserModelDTO updateUserByEmail(String email, UserUpdateDTO dto) {
        UserModel updatedUser = userDao.updateUserByEmail(email, dto);
        return modelMapper.map(updatedUser, UserModelDTO.class);
    }

    public void deactivateUserByEmail(String email) {
        UserModel user = userDao.findUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setActive(false);
        userDao.update(user);
    }

    public boolean deleteUserByEmail(String email) {
        logger.info("Starting to process deleteUserByEmail for email: {}", email);
        boolean response = userDao.deleteUserByEmail(email);

        if (response) {
            logger.info("The user with mail: {} was erased", email);
        } else {
            logger.info("The user with mail: {} wasn't erased", email);
        }

        return response;
    }

    public void changePassword(String email, PasswordChangeDTO dto) {
        UserModel user = userDao.findUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Validar que la contraseña actual coincida
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Contraseña actual incorrecta");
        }

        // Encriptar y guardar la nueva contraseña
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userDao.update(user);
    }

    public List<UserDTO> getUsers() {
        logger.info("Starting to process getUsers in service");
        List<UserModel> users = userDao.getUsers();

        if (users.isEmpty()) {
            logger.debug("No users found in service.");
        }

        // Mapear lista de usuarios a DTOs
        List<UserDTO> userDTOs = users.stream()
                .map(user -> modelMapper.map(user, UserDTO.class))
                .collect(Collectors.toList());

        logger.info("Successfully retrieved {} users", users.size());
        return userDTOs;
    }

    public Page<UserModelDTO> getUsersModel(Pageable pageable) {
        logger.info("Starting to process getUsersModel in service...");
        Page<UserModel> users = userDao.getUsersModel(pageable);

        // Mapeamos Page<UserModel> a Page<UserModelDTO> usando ModelMapper
        Page<UserModelDTO> userDTOs = users.map(userModel -> modelMapper.map(userModel, UserModelDTO.class));

        logger.info("Successfully retrieved {} users", userDTOs.getTotalElements());
        return userDTOs;
    }

    public Optional<UserModelDTO> findUserById(Long id) {
        logger.info("Starting to process search for user with ID: {}", id);

        Optional<UserModelDTO> userModelDTO = userDao.findUserById(id)
                .map(user -> modelMapper.map(user, UserModelDTO.class));

        if (userModelDTO.isEmpty()) {
            logger.debug("User with ID {} not found", id);
        }

        return userModelDTO;
    }

    public UserModelDTO updateUserById(UserModelDTO userModelDto, Long id) {
        logger.info("Starting to process update user with ID: {}", id);
        Optional<UserModel> userOptional = userDao.findUserById(id);

        if (userOptional.isEmpty()) {
            logger.debug("Update failed: User with ID {} not found", id);
            return null;
        }

        UserModel user = userOptional.get();

        user.setFirstName(userModelDto.getFirstName());
        user.setLastName(userModelDto.getLastName());
        user.setEmail(userModelDto.getEmail());
        user.setPhone(userModelDto.getPhone());
        user.setPassword(userModelDto.getPassword());

        UserModel updatedUser = userDao.updateUserById(user, id);
        logger.info("User with ID {} updated successfully", id);

        return (updatedUser != null) ? modelMapper.map(updatedUser, UserModelDTO.class) : null;
    }

    public boolean deleteUserById(long id) {
        logger.info("Starting to process attempting to delete user with ID: {}", id);
        boolean result = userDao.deleteUserById(id);

        if (result) {
            logger.info("User with ID {} deleted successfully", id);
        } else {
            logger.debug("User with ID {} not found or deletion failed", id);
        }

        return result;
    }

}