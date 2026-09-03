package com.api.crud.config;

import com.api.crud.DTO.CartDTO;
import com.api.crud.DTO.UserModelDTO;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.UserModel;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.typeMap(UserModel.class, UserModelDTO.class).addMappings(mapper -> {
            mapper.map(UserModel::getRoleNames, UserModelDTO::setRoles);
            mapper.map(UserModel::getOrderIds, UserModelDTO::setOrders);
        });

        //Cuando convierto el Cart a DTO no intento convertir todo el user model a long, tomo solo el id
        modelMapper.typeMap(Cart.class, CartDTO.class).addMappings(mapper -> {
            mapper.map(src -> src.getUserCart().getId(), CartDTO::setUserCart);
        });

        return modelMapper;
    }
}
