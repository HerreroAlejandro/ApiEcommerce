package com.api.crud.config;

import com.api.crud.DTO.UserModelDTO;
import com.api.crud.models.UserModel;
import com.api.crud.models.Role;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.stream.Collectors;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.typeMap(UserModel.class, UserModelDTO.class).addMappings(mapper -> {
            mapper.map(UserModel::getRoleNames, UserModelDTO::setRoles);
            mapper.map(UserModel::getOrderIds, UserModelDTO::setOrders);
        });

        return modelMapper;
    }
}
