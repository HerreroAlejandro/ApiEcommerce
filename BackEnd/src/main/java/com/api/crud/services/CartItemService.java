package com.api.crud.services;

import com.api.crud.DTO.CartItemAdminDetailDTO;
import com.api.crud.DTO.CartItemAdminListDTO;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.CartItem;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.CartDao;
import com.api.crud.repositories.CartItemDao;
import com.api.crud.repositories.ProductDao;
import com.api.crud.repositories.UserDao;
import com.api.crud.repositories.CartChangeAuditDao;
import com.api.crud.models.entity.CartChangeAudit;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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

    @Autowired
    private CartChangeAuditDao cartChangeAuditDao;

    private String validReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar un motivo de hasta 500 caracteres");
        }
        return reason.trim();
    }

    private void audit(UserModel actor, Cart cart, Long productId, String action,
                       int previous, int amount, String reason) {
        CartChangeAudit record = new CartChangeAudit();
        record.setActorUserId(actor.getId());
        record.setTargetUserId(cart.getUserCart().getId());
        record.setCartId(cart.getIdCart());
        record.setProductId(productId);
        record.setAction(action);
        record.setPreviousAmount(previous);
        record.setNewAmount(amount);
        record.setReason(reason);
        record.setCreatedAt(java.time.LocalDateTime.now());
        cartChangeAuditDao.save(record);
    }

    private CartItem adminItem(Long itemId) {
        CartItem item = cartItemDao.findCartItemById(itemId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
        if (!item.getCart().isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Carrito inactivo");
        }
        return item;
    }

    @Transactional
    public void addAdminItem(String email, Long userId, Long productId, int amount, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        userDao.findUserById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        positive(amount);
        Cart cart = cartDao.getOrCreateActiveCart(userId);
        int previous = cartItemDao.findByCartAndProductId(cart.getIdCart(), productId)
                .map(CartItem::getAmountCartItem).orElse(0);
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

    private void changeAdminAmount(String email, Long itemId, int amount, String action, String reason) {
        String motive = validReason(reason);
        UserModel actor = authenticatedUser(email);
        CartItem item = adminItem(itemId);
        int previous = item.getAmountCartItem();
        long next = switch (action) {
            case "INCREASE" -> (long) previous + amount;
            case "DECREASE" -> Math.max(0L, (long) previous - amount);
            default -> amount;
        };
        if (!action.equals("REMOVE") && !action.equals("DECREASE")) {
            positive(amount);
            available(item.getProductCartItem(), next);
        }
        audit(actor, item.getCart(), item.getProductCartItem().getIdProduct(), action,
                previous, (int) next, motive);
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
        Cart cart = cartDao.findCartById(cartId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrito no encontrado"));
        if (!cart.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Carrito inactivo");
        }
        if (cart.getItemsCart().isEmpty()) {
            audit(actor, cart, null, "CLEAR", 0, 0, motive);
        }
        for (CartItem item : cart.getItemsCart()) {
            audit(actor, cart, item.getProductCartItem().getIdProduct(), "CLEAR",
                    item.getAmountCartItem(), 0, motive);
        }
        cart.getItemsCart().clear();
    }

    private static final Logger logger = LoggerFactory.getLogger(CartItemService.class);

    private UserModel authenticatedUser(String email) {
        return userDao.findUserByEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private CartItem ownedItem(String email, Long itemId) {
        UserModel user = authenticatedUser(email);
        CartItem item = cartItemDao.findCartItemById(itemId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
        if (item.getCart().getUserCart().getId() != user.getId() || !item.getCart().isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado");
        }
        return item;
    }

    private void positive(int amount) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }
    }

    private void available(Product product, long amount) {
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Producto desactivado");
        }
        if (amount > Integer.MAX_VALUE || (product instanceof com.api.crud.models.entity.ProductPhysical physical
                && amount > physical.getStockProduct())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cantidad no disponible");
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

        Product product = productDao.findProductById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        Optional<CartItem> existingCartItem = cartItemDao.findByCartAndProductId(cart.getIdCart(), productId);
        long resultingAmount = (long) amount + existingCartItem.map(CartItem::getAmountCartItem).orElse(0);
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
    public Optional<CartItemAdminDetailDTO> findCartItemById(Long cartItemId) {
        return cartItemDao.findCartItemById(cartItemId).map(item -> {
            Product product = item.getProductCartItem();
            CartItemAdminDetailDTO dto = new CartItemAdminDetailDTO();
            dto.setIdCartItem(item.getIdCartItem());
            dto.setCartId(item.getCart().getIdCart());
            dto.setProductId(product.getIdProduct());
            dto.setNameProduct(product.getNameProduct());
            dto.setAmountCartItem(item.getAmountCartItem());
            dto.setPriceProduct(product.getPriceProduct());
            dto.setSubtotal(item.getSubtotal());
            dto.setImageUrl(product.getImageUrl());
            dto.setType(product.getType());
            dto.setActive(product.isActive());
            if (product instanceof com.api.crud.models.entity.ProductPhysical physical) {
                dto.setStockProduct(physical.getStockProduct());
            }
            return dto;
        });
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CartItemAdminListDTO> findItemsByCartId(Long cartId) {
        cartDao.findCartById(cartId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Carrito no encontrado"));
        return cartItemDao.findItemsByCartId(cartId).stream().map(item -> {
            CartItemAdminListDTO dto = new CartItemAdminListDTO();
            dto.setIdCartItem(item.getIdCartItem());
            dto.setProductId(item.getProductCartItem().getIdProduct());
            dto.setNameProduct(item.getProductCartItem().getNameProduct());
            dto.setAmountCartItem(item.getAmountCartItem());
            dto.setPriceProduct(item.getProductCartItem().getPriceProduct());
            dto.setSubtotal(item.getSubtotal());
            return dto;
        }).toList();
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
            logger.error("Error al actualizar la cantidad del artÃƒÂ­culo: ", e);
            throw new RuntimeException("No se pudo actualizar la cantidad del artÃƒÂ­culo");
        }
    }

    public void increaseItemAmount(Long cartItemId, int increment) {
        cartItemDao.increaseCartItemAmount(cartItemId, increment);
    }

    public void decreaseItemAmount(Long cartItemId, int decrement) {
        cartItemDao.decreaseCartItemAmount(cartItemId, decrement);
    }

}
