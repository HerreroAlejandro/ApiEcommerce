package com.api.crud.config;

import com.api.crud.DTO.CartDTO;
import com.api.crud.DTO.CartItemDTO;
import com.api.crud.DTO.UserDetailAdminResponseDTO;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.CartItem;
import com.api.crud.models.entity.UserModel;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.typeMap(UserModel.class, UserDetailAdminResponseDTO.class)
                .addMappings(mapper -> {
                    mapper.map(UserModel::getRoleNames, UserDetailAdminResponseDTO::setRoles);
                });

        //Cuando convierto el Cart a DTO no intento convertir todo el user model a long, tomo solo el id
        modelMapper.typeMap(Cart.class, CartDTO.class).addMappings(mapper -> {
            mapper.map(src -> src.getUserCart().getId(), CartDTO::setUserCart);
        });

        modelMapper.typeMap(CartItem.class, CartItemDTO.class).addMappings(mapper -> {
            mapper.map(src -> src.getProductCartItem().getImageUrl(), CartItemDTO::setImageUrl);
        });

        return modelMapper;
    }
}
