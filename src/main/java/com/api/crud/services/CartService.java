package com.api.crud.services;

import com.api.crud.DTO.CartDTO;
import com.api.crud.DTO.CartItemDTO;
import com.api.crud.models.Cart;
import com.api.crud.repositories.CartDao;
import com.api.crud.repositories.CartItemDao;
import com.api.crud.repositories.ProductDao;
import com.api.crud.repositories.UserDao;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class CartService {

    @Autowired
    private CartDao cartDao;

    @Autowired
    private CartItemDao cartItemDao;

    @Autowired
    private ProductDao productDao;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private UserDao userDao;

    private static final Logger logger = LoggerFactory.getLogger(CartService.class);


    public void saveCart(CartDTO cartDTO) {
        try {
            Cart cart = modelMapper.map(cartDTO, Cart.class);
            cartDao.saveCart(cart);
        } catch (Exception e) {
            logger.error("Error guardando el carrito: ", e);
            throw new RuntimeException("No se pudo guardar el carrito");
        }
    }

    public List<CartDTO> getCarts() {
        return cartDao.getCarts()
                .stream()
                .map(cart -> modelMapper.map(cart, CartDTO.class))
                .collect(Collectors.toList());
    }

    public Optional<CartDTO> findCartByUserEmail(String email) {
        return cartDao.findCartByUserEmail(email)
                .map(cart -> modelMapper.map(cart, CartDTO.class));
    }

    public Optional<CartDTO> findCartById(Long idCart) {
        return cartDao.findCartById(idCart)
                .map(cart -> modelMapper.map(cart, CartDTO.class));
    }

    public Optional<CartDTO> findCartByUserId(Long userId) {
        return cartDao.findCartByUserId(userId)
                .map(cart -> modelMapper.map(cart, CartDTO.class));
    }

    public void deleteCartById(Long idCart) {
        try {
            cartDao.deleteCartById(idCart);
        } catch (Exception e) {
            logger.error("Error eliminando el carrito: ", e);
            throw new RuntimeException("No se pudo eliminar el carrito");
        }
    }

    public Optional<CartItemDTO> findByCartAndProductId(Long cartId, Long productId) {
        return cartItemDao.findByCartAndProductId(cartId, productId)
                .map(item -> modelMapper.map(item, CartItemDTO.class));
    }

    public Optional<CartDTO> findActiveCartByUserId(Long userId) {
        return cartDao.findActiveCartByUserId(userId)
                .map(cart -> modelMapper.map(cart, CartDTO.class));
    }

    public List<CartDTO> getCartsByDateRange(LocalDate start, LocalDate end) {
        return cartDao.getCartsByDateRange(start, end)
                .stream()
                .map(cart -> modelMapper.map(cart, CartDTO.class))
                .collect(Collectors.toList());
    }

    public List<CartDTO> getCartsWithMoreThanNItems(int minItems) {
        return cartDao.getCartsWithMoreThanNItems(minItems)
                .stream()
                .map(cart -> modelMapper.map(cart, CartDTO.class))
                .collect(Collectors.toList());
    }

}