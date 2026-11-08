package com.api.crud.services;

import com.api.crud.DTO.CartAdminDetailDTO;
import com.api.crud.DTO.CartAdminFilterDTO;
import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.DTO.MyCartResponseDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.CartItem;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.CartDao;
import com.api.crud.repositories.CartItemDao;
import com.api.crud.repositories.ProductDao;
import com.api.crud.repositories.UserDao;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private static final Logger logger = LoggerFactory.getLogger(CartService.class);

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

    private Optional<Cart> getMyCart(String email) {
        UserModel user =
                userDao.getUserByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return cartDao.getActiveCartByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public MyCartResponseDTO getMyActiveCart(String email) {
        return modelMapper.map(getMyCart(email).orElseGet(Cart::new), MyCartResponseDTO.class);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMyCartTotal(String email) {
        return getMyCart(email).map(cart -> getCartTotal(cart.getIdCart())).orElse(BigDecimal.ZERO);
    }

    @Transactional
    public void clearMyCart(String email) {
        getMyCart(email).ifPresent(cart -> cart.getItemsCart().clear());
    }

    @Transactional(readOnly = true)
    public Page<CartAdminListDTO> getCarts(CartAdminFilterDTO filter, Pageable pageable) {
        if (filter.getMinTotal() != null
                && filter.getMaxTotal() != null
                && filter.getMinTotal().compareTo(filter.getMaxTotal()) > 0) {
            throw new InvalidRequestException("El importe minimo supera al maximo");
        }
        if (filter.getMinProductQuantity() != null && filter.getProductId() == null) {
            throw new InvalidRequestException("Debe indicar productId al filtrar por cantidad");
        }
        if (pageable.getPageSize() > 100 || pageable.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidRequestException("Paginacion fuera del limite");
        }
        return cartDao.getCarts(filter, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<CartAdminDetailDTO> getCartByUserEmail(String email) {
        return cartDao.getCartByUserEmail(email)
                .map(cart -> modelMapper.map(cart, CartAdminDetailDTO.class));
    }

    @Transactional(readOnly = true)
    public Optional<CartAdminDetailDTO> getCartById(Long id) {
        return cartDao.getCartById(id).map(cart -> modelMapper.map(cart, CartAdminDetailDTO.class));
    }

    @Transactional(readOnly = true)
    public Optional<CartAdminDetailDTO> getCartByUserId(Long id) {
        return cartDao.getCartByUserId(id)
                .map(cart -> modelMapper.map(cart, CartAdminDetailDTO.class));
    }

    @Transactional(readOnly = true)
    public List<CartAdminListDTO> getCartsByDateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new InvalidRequestException("Rango de fechas invalido");
        }
        return cartDao.getCartsByDateRange(start, end).stream()
                .map(cart -> modelMapper.map(cart, CartAdminListDTO.class))
                .toList();
    }

    public BigDecimal getCartTotal(Long cartId) {
        return cartItemDao.getItemsByCartId(cartId).stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
