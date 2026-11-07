package com.api.crud.services;

import com.api.crud.DTO.CartDTO;
import com.api.crud.DTO.CartItemDTO;
import com.api.crud.DTO.MyCartResponseDTO;
import com.api.crud.DTO.MyCartItemResponseDTO;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.CartItem;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.CartDao;
import com.api.crud.repositories.CartItemDao;
import com.api.crud.repositories.ProductDao;
import com.api.crud.repositories.UserDao;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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

    private Optional<Cart> findMyCart(String email) {
        UserModel user = userDao.findUserByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return cartDao.findActiveCartByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public MyCartResponseDTO findMyActiveCart(String email) {
        return findMyCart(email).map(cart -> {
            List<MyCartItemResponseDTO> items = cart.getItemsCart().stream()
                    .map(this::toMyCartItemResponse).toList();
            MyCartResponseDTO response = new MyCartResponseDTO();
            response.setItemsCart(items);
            response.setTotal(items.stream().map(MyCartItemResponseDTO::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            return response;
        }).orElseGet(() -> {
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
        response.setSubtotal(item.getPriceCartItem());
        return response;
    }

    @Transactional(readOnly = true)
    public BigDecimal getMyCartTotal(String email) {
        return findMyCart(email).map(cart -> getCartTotal(cart.getIdCart()))
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public void clearMyCart(String email) {
        findMyCart(email).ifPresent(cart -> cart.getItemsCart().clear());
    }


    public void saveCart(CartDTO cartDTO) {
        try {
            Cart cart = modelMapper.map(cartDTO, Cart.class);

            UserModel user = userDao.findUserById(cartDTO.getUserCart())
                    .orElseThrow(() ->
                            new RuntimeException("Usuario no encontrado"));

            cart.setUserCart(user);
            cart.setCreationDate(LocalDate.now());

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

    public boolean deleteCartById(Long idCart) {
        try {
            return cartDao.deleteCartById(idCart);
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

    public BigDecimal getCartTotal(Long cartId) {
        List<CartItem> items = cartItemDao.findItemsByCartId(cartId);
        return items.stream()
                .map(CartItem::getPriceCartItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void clearCart(Long cartId) {
        List<CartItem> items = cartItemDao.findItemsByCartId(cartId);
        for (CartItem item : items) {
            cartItemDao.removeCartItem(item.getIdCartItem());
        }
    }


}
