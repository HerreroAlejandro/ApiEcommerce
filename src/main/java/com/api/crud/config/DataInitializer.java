package com.api.crud.config;


import com.api.crud.models.entity.Role;
import com.api.crud.repositories.RoleDao;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(1)
public class DataInitializer {

    @Bean
    CommandLineRunner initRoles(RoleDao roleDao) {
        return args -> {

            createRoleIfNotExists(roleDao, "ADMIN");
            createRoleIfNotExists(roleDao, "SUPPORT");
            createRoleIfNotExists(roleDao, "CLIENT");

        };
    }

    private void createRoleIfNotExists(RoleDao roleDao, String roleName) {

        if (roleDao.findRoleByName(roleName).isEmpty()) {

            Role role = new Role();
            role.setNameRole(roleName);

            roleDao.saveRole(role);

            System.out.println("Rol creado: " + roleName);

        } else {

            System.out.println("El rol ya existe: " + roleName);
        }
    }
}
