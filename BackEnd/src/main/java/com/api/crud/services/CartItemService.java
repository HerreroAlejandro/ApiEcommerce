package com.api.crud.services;

import com.api.crud.DTO.CartItemAdminDetailDTO;
import com.api.crud.DTO.CartItemAdminListDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.CartChangeAudit;
import com.api.crud.models.entity.CartItem;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.ProductDigital;
import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.CartChangeAuditDao;
import com.api.crud.repositories.CartDao;
import com.api.crud.repositories.CartItemDao;
import com.api.crud.repositories.ProductDao;
import com.api.crud.repositories.UserDao;

import jakarta.transaction.Transactional;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CartItemService {

    private static final Logger logger = LoggerFactory.getLogger(CartItemService.class);

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

    @Autowired
    private CartChangeAuditDao cartChangeAuditDao;

    private String validReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new InvalidRequestException("Debe indicar un motivo de hasta 500 caracteres");
        }
        return reason.trim();
    }

    private void audit(
            UserModel actor,
            Cart cart,
            Long productId,
            String action,
            int previous,
            int amount,
            String reason) {
        CartChangeAudit record = new CartChangeAudit();
        record.setActorUserId(actor.getId());
        record.setTargetUserId(cart.getUserCart().getId());
        record.setCartId(cart.getIdCart());
        record.setProductId(productId);
        record.setAction(action);
        record.setPreviousAmount(previous);
        record.setNewAmount(amount);
        record.setReason(reason);
        record.setCreatedAt(LocalDateTime.now());
        cartChangeAuditDao.save(record);
    }

    private CartItem adminItem(Long itemId) {
        CartItem item =
                cartItemDao
                        .getCartItemById(itemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado"));
        if (!item.getCart().isActive()) {
            throw new BusinessConflictException("Carrito inactivo");
        }
        return item;
    }

    @Transactional
    public void changeAdminCartState(String email, Long cartId, boolean active, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        Cart cart =
                cartDao.getCartById(cartId)
                        .orElseThrow(() -> new ResourceNotFoundException("Carrito no encontrado"));
        if (cart.isActive() != active) {
            audit(actor, cart, null, active ? "ENABLE" : "DISABLE", 0, 0, motive);
            cart.setActive(active);
        }
    }

    @Transactional
    public void deleteAdminCart(String email, Long cartId, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        Cart cart =
                cartDao.getCartById(cartId)
                        .orElseThrow(() -> new ResourceNotFoundException("Carrito no encontrado"));
        for (CartItem item : cart.getItemsCart()) {
            audit(
                    actor,
                    cart,
                    item.getProductCartItem().getIdProduct(),
                    "DELETE_ITEM",
                    item.getAmountCartItem(),
                    0,
                    motive);
        }
        audit(actor, cart, null, "DELETE_CART", 0, 0, motive);
        cartDao.deleteCartById(cartId);
    }

    @Transactional
    public void addAdminItem(String email, Long userId, Long productId, int amount, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        userDao.getUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        positive(amount);
        Cart cart = cartDao.getOrCreateActiveCart(userId);
        int previous =
                cartItemDao
                        .getByCartAndProductId(cart.getIdCart(), productId)
                        .map(CartItem::getAmountCartItem)
                        .orElse(0);
        addItemToCart(userId, productId, amount);
        audit(actor, cart, productId, "ADD", previous, previous + amount, motive);
    }

    @Transactional
    public void updateAdminItemAmount(String email, Long itemId, int amount, String reason) {
        changeAdminAmount(email, itemId, amount, "SET", reason);
    }

    @Transactional
    public void increaseAdminItemAmount(String email, Long itemId, int amount, String reason) {
        positive(amount);
        changeAdminAmount(email, itemId, amount, "INCREASE", reason);
    }

    @Transactional
    public void decreaseAdminItemAmount(String email, Long itemId, int amount, String reason) {
        positive(amount);
        changeAdminAmount(email, itemId, amount, "DECREASE", reason);
    }

    @Transactional
    public void removeAdminItem(String email, Long itemId, String reason) {
        changeAdminAmount(email, itemId, 0, "REMOVE", reason);
    }

    private void changeAdminAmount(
            String email, Long itemId, int amount, String action, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        CartItem item = adminItem(itemId);
        int previous = item.getAmountCartItem();
        long next =
                switch (action) {
                    case "INCREASE" -> (long) previous + amount;
                    case "DECREASE" -> Math.max(0L, (long) previous - amount);
                    default -> amount;
                };
        if (!action.equals("REMOVE") && !action.equals("DECREASE")) {
            positive(amount);
            available(item.getProductCartItem(), next);
        }
        audit(
                actor,
                item.getCart(),
                item.getProductCartItem().getIdProduct(),
                action,
                previous,
                (int) next,
                motive);
        if (next == 0) {
            item.getCart().getItemsCart().remove(item);
            cartItemDao.removeCartItem(itemId);
        } else {
            item.setAmountCartItem((int) next);
        }
    }

    @Transactional
    public void clearAdminCart(String email, Long cartId, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        Cart cart =
                cartDao.getCartById(cartId)
                        .orElseThrow(() -> new ResourceNotFoundException("Carrito no encontrado"));
        if (!cart.isActive()) {
            throw new BusinessConflictException("Carrito inactivo");
        }
        if (cart.getItemsCart().isEmpty()) {
            audit(actor, cart, null, "CLEAR", 0, 0, motive);
        }
        for (CartItem item : cart.getItemsCart()) {
            audit(
                    actor,
                    cart,
                    item.getProductCartItem().getIdProduct(),
                    "CLEAR",
                    item.getAmountCartItem(),
                    0,
                    motive);
        }
        cart.getItemsCart().clear();
    }

    private UserModel authenticatedUser(String email) {
        return userDao.getUserByEmail(email).orElseThrow(() -> new UnauthenticatedException());
    }

    private CartItem ownedItem(String email, Long itemId) {
        UserModel user = authenticatedUser(email);
        CartItem item =
                cartItemDao
                        .getCartItemById(itemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado"));
        if (item.getCart().getUserCart().getId() != user.getId() || !item.getCart().isActive()) {
            throw new ResourceNotFoundException("Item no encontrado");
        }
        return item;
    }

    private void positive(int amount) {
        if (amount <= 0 || amount > 99999999) {
            throw new InvalidRequestException("La cantidad debe estar entre 1 y 99999999");
        }
    }

    private void available(Product product, long amount) {
        if (product instanceof ProductDigital && amount != 1) {
            throw new BusinessConflictException("Solo se permite una unidad por producto digital");
        }
        if (!product.isActive()) {
            throw new BusinessConflictException("Producto desactivado");
        }
        if (amount > 99999999
                || (product instanceof ProductPhysical physical
                        && amount > physical.getStockProduct())) {
            throw new InsufficientStockException("Cantidad no disponible");
        }
    }

    @Transactional
    public void addMyItem(String email, Long productId, int amount) {
        addItemToCart(authenticatedUser(email).getId(), productId, amount);
    }

    @Transactional
    public void removeMyItem(String email, Long itemId) {
        CartItem item = ownedItem(email, itemId);
        item.getCart().getItemsCart().remove(item);
        cartItemDao.removeCartItem(itemId);
    }

    @Transactional
    public void updateMyItemAmount(String email, Long itemId, int amount) {
        positive(amount);
        CartItem item = ownedItem(email, itemId);
        available(item.getProductCartItem(), amount);
        item.setAmountCartItem(amount);
    }

    @Transactional
    public void increaseMyItemAmount(String email, Long itemId, int increment) {
        positive(increment);
        CartItem item = ownedItem(email, itemId);
        long amount = (long) item.getAmountCartItem() + increment;
        available(item.getProductCartItem(), amount);
        item.setAmountCartItem((int) amount);
    }

    @Transactional
    public void decreaseMyItemAmount(String email, Long itemId, int decrement) {
        positive(decrement);
        CartItem item = ownedItem(email, itemId);
        if (decrement >= item.getAmountCartItem()) {
            item.getCart().getItemsCart().remove(item);
            cartItemDao.removeCartItem(itemId);
        } else {
            item.setAmountCartItem(item.getAmountCartItem() - decrement);
        }
    }

    @Transactional
    public void addItemToCart(Long userId, Long productId, int amount) {
        positive(amount);
        Cart cart = cartDao.getOrCreateActiveCart(userId);
        Product product =
                productDao
                        .getProductById(productId)
                        .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        Optional<CartItem> existingCartItem =
                cartItemDao.getByCartAndProductId(cart.getIdCart(), productId);
        long resultingAmount =
                (long) amount + existingCartItem.map(CartItem::getAmountCartItem).orElse(0);
        available(product, resultingAmount);
        if (existingCartItem.isPresent()) {
            CartItem cartItem = existingCartItem.get();
            cartItem.setAmountCartItem(cartItem.getAmountCartItem() + amount);
            cartItemDao.saveCartItem(cartItem);
        } else {
            CartItem newCartItem = new CartItem();
            newCartItem.setCart(cart);
            newCartItem.setProductCartItem(product);
            newCartItem.setAmountCartItem(amount);
            cartItemDao.saveCartItem(newCartItem);
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<CartItemAdminDetailDTO> getCartItemById(Long cartItemId) {
        return cartItemDao
                .getCartItemById(cartItemId)
                .map(item -> modelMapper.map(item, CartItemAdminDetailDTO.class));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CartItemAdminListDTO> getItemsByCartId(Long cartId) {
        cartDao.getCartById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrito no encontrado"));
        return cartItemDao.getItemsByCartId(cartId).stream()
                .map(item -> modelMapper.map(item, CartItemAdminListDTO.class))
                .toList();
    }
}
