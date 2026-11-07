package com.api.crud.config;

import com.api.crud.models.entity.Role;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.RoleDao;
import com.api.crud.repositories.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Configuration
@Order(2)
public class AdminInitializer {

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Bean
    CommandLineRunner createAdmin() {
        return args -> {

            Optional<UserModel> existingAdmin = userDao.findUserByEmail(adminEmail);

            if (existingAdmin.isPresent()) {
                System.out.println("El usuario ADMIN ya existe: " + adminEmail);
                return;
            }

            Role adminRole = roleDao.findRoleByName("ADMIN")
                    .orElseThrow(() ->
                            new RuntimeException("El rol ADMIN no existe en la base de datos"));

            UserModel admin = new UserModel();

            admin.setFirstName("Alejandro Ariel");
            admin.setLastName("Herrero");
            admin.setEmail(adminEmail);
            admin.setPhone(null);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setActive(true);

            admin.getRoles().add(adminRole);

            userDao.register(admin);

            System.out.println("======================================");
            System.out.println("Usuario ADMIN creado correctamente");
            System.out.println("Email: " + adminEmail);
            System.out.println("======================================");
        };
    }
}
