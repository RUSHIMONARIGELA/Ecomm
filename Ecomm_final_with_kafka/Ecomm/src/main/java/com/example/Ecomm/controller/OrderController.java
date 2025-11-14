package com.example.Ecomm.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Ecomm.config.SecurityConstants;
import com.example.Ecomm.dto.CustomerDTO;
import com.example.Ecomm.dto.OrderDTO;
import com.example.Ecomm.dto.UserDTO;
import com.example.Ecomm.exception.ResourceNotFoundException;
import com.example.Ecomm.service.CustomerService;
import com.example.Ecomm.service.OrderService;
import com.example.Ecomm.service.UserService;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:4200")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserService userService;

    public Long getAuthenticatedCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUsername = authentication.getName();
        UserDTO userDTO = userService.getUserByUserName(authenticatedUsername);
        return userDTO.getId();
    }

    public Long getCustomerIdByOrderId(Long orderId) {
        OrderDTO orderDTO = orderService.getOrderById(orderId);
        if (orderDTO != null && orderDTO.getCustomerId() != null) {
            return orderDTO.getCustomerId();
        }
        throw new ResourceNotFoundException("Order", "Id", orderId);
    }


    @GetMapping
    @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_ADMIN + "')")
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        List<OrderDTO> orders = orderService.getAllOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_ADMIN + "') or @orderController.getAuthenticatedCustomerId() == @orderController.getCustomerIdByOrderId(#orderId)")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long orderId) {
        OrderDTO order = orderService.getOrderById(orderId);
        return new ResponseEntity<>(order, HttpStatus.OK);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_ADMIN + "') or #customerId == @orderController.getAuthenticatedCustomerId()")
    public ResponseEntity<List<OrderDTO>> getOrdersByCustomerId(@PathVariable Long customerId) {
        List<OrderDTO> orders = orderService.getOrdersByCustomerId(customerId);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @PostMapping("/from-cart/{customerId}")
    @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_CUSTOMER + "') and #customerId == @orderController.getAuthenticatedCustomerId()")
    public ResponseEntity<OrderDTO> createOrderFromCart(@PathVariable Long customerId) {
        OrderDTO createdOrder = orderService.placeOrder(customerId);
        return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_ADMIN + "')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long orderId) {
        orderService.deleteOrder(orderId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

//    @PutMapping("/{orderId}")
//    @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_ADMIN + "')")
//    public ResponseEntity<OrderDTO> updateOrder(@PathVariable Long orderId, @Validated @RequestBody String status) {
//        OrderDTO updatedOrder = orderService.updateOrderStatus(orderId, status);
//        return new ResponseEntity<>(updatedOrder, HttpStatus.OK);
//    }
 // OrderController.java
 // Create a new DTO specifically for updates if needed, or reuse OrderDTO
 // We will assume a new service method `updateOrderFull` is used
 @PutMapping("/{orderId}")
 @PreAuthorize("hasAuthority('" + SecurityConstants.ROLE_ADMIN + "')")
 public ResponseEntity<OrderDTO> updateOrder(@PathVariable Long orderId, @Validated @RequestBody OrderDTO orderDTO) {
     // Call a new service method that handles full DTO updates
     OrderDTO updatedOrder = orderService.updateOrderFull(orderId, orderDTO); 
     return new ResponseEntity<>(updatedOrder, HttpStatus.OK);
 }
}
