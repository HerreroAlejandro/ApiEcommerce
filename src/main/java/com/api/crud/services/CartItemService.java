package com.api.crud.services;

import com.api.crud.DTO.CartItemDTO;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.CartItem;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.CartDao;
import com.api.crud.repositories.CartItemDao;
import com.api.crud.repositories.ProductDao;
import com.api.crud.repositories.UserDao;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CartItemService {

    @Autowired
    private CartItemDao cartItemDao;

    @Autowired
    private ProductDao productDao;

    @Autowired
    private CartDao cartDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(CartItemService.class);

    @Transactional
    public void addItemToCart(Long userId, Long productId, int amount) {
        Cart cart = cartDao.findActiveCartByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    UserModel user = userDao.findUserById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                    newCart.setUserCart(user);
                    newCart.setActive(true);
                    newCart.setCreationDate(LocalDate.now());
                    cartDao.saveCart(newCart);
                    return newCart;
                });

        Product product = productDao.findProductById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        Optional<CartItem> existingCartItem = cartItemDao.findByCartAndProductId(cart.getIdCart(), productId);
        BigDecimal price = product.getPriceProduct().multiply(BigDecimal.valueOf(amount));

        if (existingCartItem.isPresent()) {
            CartItem cartItem = existingCartItem.get();
            cartItem.setAmountCartItem(cartItem.getAmountCartItem() + amount);
            cartItem.setPriceCartItem(cartItem.getPriceCartItem().add(price));
            cartItemDao.saveCartItem(cartItem);
        } else {
            CartItem newCartItem = new CartItem();
            newCartItem.setCart(cart);
            newCartItem.setProductCartItem(product);
            newCartItem.setAmountCartItem(amount);
            newCartItem.setPriceCartItem(price);
            cartItemDao.saveCartItem(newCartItem);
        }
    }

    public List<CartItemDTO> findItemsByCartId(Long cartId) {
        return cartItemDao.findItemsByCartId(cartId)
                .stream()
                .map(item -> modelMapper.map(item, CartItemDTO.class))
                .collect(Collectors.toList());
    }

    public Optional<CartItemDTO> findCartItemById(Long cartItemId) {
        return cartItemDao.findCartItemById(cartItemId)
                .map(item -> modelMapper.map(item, CartItemDTO.class));
    }

    @Transactional
    public void removeItemFromCart(Long cartId, Long cartItemId) {
        Cart cart = cartDao.findCartById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

        CartItem cartItem = cartItemDao.findCartItemById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado en el carrito"));

        if (!cartItem.getCart().getIdCart().equals(cartId)) {
            throw new IllegalArgumentException("El item no pertenece a este carrito.");
        }

        cartItemDao.removeCartItem(cartItemId);
    }

    public void updateCartItemAmount(Long cartItemId, int newAmount) {
        try {
            cartItemDao.updateCartItemAmount(cartItemId, newAmount);
        } catch (Exception e) {
            logger.error("Error al actualizar la cantidad del artículo: ", e);
            throw new RuntimeException("No se pudo actualizar la cantidad del artículo");
        }
    }

    public List<CartItemDTO> getItemsFullByCartId(Long cartId) {
        return cartItemDao.findItemsByCartId(cartId)
                .stream()
                .map(this::toFullDTO)
                .collect(Collectors.toList());
    }

    private CartItemDTO mapToDTO(CartItem cartItem) {
        CartItemDTO dto = modelMapper.map(cartItem, CartItemDTO.class);
        dto.setCartId(cartItem.getCart().getIdCart());
        dto.setProductId(cartItem.getProductCartItem().getIdProduct());
        // Aquí podrías agregar más info del producto si querés mostrarlo en el DTO
        return dto;
    }

    //Para mostrarle al cliente
    public CartItemDTO toFullDTO(CartItem cartItem) {
        return new CartItemDTO(
                cartItem.getIdCartItem(),
                cartItem.getCart().getIdCart(),
                cartItem.getProductCartItem().getIdProduct(),
                cartItem.getProductCartItem().getNameProduct(),
                cartItem.getProductCartItem().getPriceProduct(),
                cartItem.getProductCartItem().getImageUrl(),
                cartItem.getAmountCartItem(),
                cartItem.getPriceCartItem()
        );
    }

    public void increaseItemAmount(Long cartItemId, int increment) {
        cartItemDao.increaseCartItemAmount(cartItemId, increment);
    }

    public void decreaseItemAmount(Long cartItemId, int decrement) {
        cartItemDao.decreaseCartItemAmount(cartItemId, decrement);
    }

}
