package com.api.crud.services;

import com.api.crud.DTO.CartAdminDetailDTO;
import com.api.crud.DTO.CartAdminFilterDTO;
import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.DTO.CartItemAdminListDTO;
import com.api.crud.DTO.MyCartItemResponseDTO;
import com.api.crud.DTO.MyCartResponseDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                        .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return cartDao.getActiveCartByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public MyCartResponseDTO getMyActiveCart(String email) {
        return getMyCart(email)
                .map(
                        cart -> {
                            List<MyCartItemResponseDTO> items =
                                    cart.getItemsCart().stream()
                                            .map(this::toMyCartItemResponse)
                                            .toList();
                            MyCartResponseDTO response = new MyCartResponseDTO();
                            response.setItemsCart(items);
                            response.setTotal(
                                    items.stream()
                                            .map(MyCartItemResponseDTO::getSubtotal)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                            return response;
                        })
                .orElseGet(
                        () -> {
                            MyCartResponseDTO response = new MyCartResponseDTO();
                            response.setItemsCart(List.of());
                            response.setTotal(BigDecimal.ZERO);
                            return response;
                        });
    }

    private MyCartItemResponseDTO toMyCartItemResponse(CartItem item) {
        MyCartItemResponseDTO response = new MyCartItemResponseDTO();
        response.setIdCartItem(item.getIdCartItem());
        response.setProductId(item.getProductCartItem().getIdProduct());
        response.setNameProduct(item.getProductCartItem().getNameProduct());
        response.setImageUrl(item.getProductCartItem().getImageUrl());
        response.setPriceProduct(item.getProductCartItem().getPriceProduct());
        response.setAmountCartItem(item.getAmountCartItem());
        response.setSubtotal(item.getSubtotal());
        return response;
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El importe minimo supera al maximo");
        }
        if (filter.getMinProductQuantity() != null && filter.getProductId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Debe indicar productId al filtrar por cantidad");
        }
        if (pageable.getPageSize() > 100 || pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Paginacion fuera del limite");
        }
        return cartDao.getCarts(filter, pageable);
    }

    private CartAdminListDTO toAdminList(Cart cart) {
        CartAdminListDTO dto = new CartAdminListDTO();
        dto.setIdCart(cart.getIdCart());
        dto.setUserId(cart.getUserCart().getId());
        dto.setActive(cart.isActive());
        dto.setCreationDate(cart.getCreationDate());
        dto.setItemCount(cart.getItemsCart().size());
        dto.setTotal(
                cart.getItemsCart().stream()
                        .map(CartItem::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        return dto;
    }

    private CartAdminDetailDTO toAdminDetail(Cart cart) {
        CartAdminListDTO summary = toAdminList(cart);
        CartAdminDetailDTO dto = new CartAdminDetailDTO();
        dto.setIdCart(summary.getIdCart());
        dto.setUserId(summary.getUserId());
        dto.setActive(summary.isActive());
        dto.setCreationDate(summary.getCreationDate());
        dto.setItemCount(summary.getItemCount());
        dto.setTotal(summary.getTotal());
        dto.setItemsCart(
                cart.getItemsCart().stream()
                        .map(
                                item -> {
                                    CartItemAdminListDTO row = new CartItemAdminListDTO();
                                    row.setIdCartItem(item.getIdCartItem());
                                    row.setProductId(item.getProductCartItem().getIdProduct());
                                    row.setNameProduct(item.getProductCartItem().getNameProduct());
                                    row.setAmountCartItem(item.getAmountCartItem());
                                    row.setPriceProduct(
                                            item.getProductCartItem().getPriceProduct());
                                    row.setSubtotal(item.getSubtotal());
                                    return row;
                                })
                        .toList());
        return dto;
    }

    @Transactional(readOnly = true)
    public Optional<CartAdminDetailDTO> getCartByUserEmail(String email) {
        return cartDao.getCartByUserEmail(email).map(this::toAdminDetail);
    }

    @Transactional(readOnly = true)
    public Optional<CartAdminDetailDTO> getCartById(Long id) {
        return cartDao.getCartById(id).map(this::toAdminDetail);
    }

    @Transactional(readOnly = true)
    public Optional<CartAdminDetailDTO> getCartByUserId(Long id) {
        return cartDao.getCartByUserId(id).map(this::toAdminDetail);
    }

    @Transactional(readOnly = true)
    public List<CartAdminListDTO> getCartsByDateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas invalido");
        }
        return cartDao.getCartsByDateRange(start, end).stream().map(this::toAdminList).toList();
    }

    public BigDecimal getCartTotal(Long cartId) {
        return cartItemDao.getItemsByCartId(cartId).stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
