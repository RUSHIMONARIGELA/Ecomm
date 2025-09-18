package com.example.Ecomm.service;

import com.example.Ecomm.dto.CartDTO;
import com.example.Ecomm.entitiy.Cart;


public interface CartService {
	Cart getOrCreateCart(Long customerId); 

    CartDTO getCartByCustomerId(Long customerId); 
    CartDTO addProductToCart(Long customerId, Long productId, Long quantity);
    CartDTO updateProductQuantityInCart(Long customerId, Long productId, Long newQuantity);
    CartDTO removeProductFromCart(Long customerId, Long productId);
    void clearCart(Long customerId);
    CartDTO getCartById(Long cartId);

    CartDTO applyCouponToCart(Long customerId, String couponCode);
    CartDTO removeCouponFromCart(Long customerId);
}
