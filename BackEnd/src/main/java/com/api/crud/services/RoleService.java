package com.api.crud.services;

import com.api.crud.DTO.RoleDTO;
import com.api.crud.models.entity.Role;
import com.api.crud.repositories.RoleDao;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoleService {

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(RoleService.class);

    public Optional<RoleDTO> findRoleByName(String roleName) {
        Optional<RoleDTO> roleDTO = roleDao.findRoleByName(roleName)
                .map(role -> modelMapper.map(role, RoleDTO.class));

        if (roleDTO.isPresent()) {
            logger.debug("Role {} found in RoleService.", roleName);
        } else {
            logger.debug("Role {} not found in RoleService.", roleName);
        }
        return roleDTO;
    }

    public Optional<RoleDTO> findRoleById(Long id){
        logger.info("Starting to process findRoleBy id with id {} in services", id);
        Optional<RoleDTO> roleDTO = roleDao.findRoleById(id)
                .map(role -> modelMapper.map(role, RoleDTO.class));

        if (roleDTO.isPresent()){
            logger.debug("Role with id {} was found in services", id);
        } else {
            logger.debug("Role with id {} wasn't found in services", id);
        }
        return roleDTO;
    }

    public List<RoleDTO> getRoles() {
        logger.debug("Retrieving all roles from RoleService...");
        List<RoleDTO> roleDTOs = roleDao.getRoles()
                .stream()
                .map(role -> modelMapper.map(role, RoleDTO.class))
                .collect(Collectors.toList());

        logger.debug("Successfully retrieved {} roles from RoleService.", roleDTOs.size());
        return roleDTOs;
    }

    public void saveRole(RoleDTO roleDTO) {
        logger.info("Starting to save new role: {} in services", roleDTO.getNameRole());
        Role role = modelMapper.map(roleDTO, Role.class);
        roleDao.saveRole(role);
        logger.info("Role {} saved successfully in RoleService.", roleDTO.getNameRole());
    }

    public boolean deleteRoleById(Long id) {
        logger.info("Starting to delete role with id {} in services", id);
        boolean result = roleDao.deleteRoleById(id);
        if (result) {
            logger.info("Role with ID {} deleted successfully", id);
        } else {
            logger.debug("Role with ID {} not found or deletion failed", id);
        }
        return result;
    }
}