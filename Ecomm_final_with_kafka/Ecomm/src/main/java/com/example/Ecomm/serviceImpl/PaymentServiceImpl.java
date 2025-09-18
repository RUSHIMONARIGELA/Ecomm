package com.example.Ecomm.serviceImpl;



import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Date;
import org.json.JSONObject;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.Ecomm.dto.PaymentDTO;
import com.example.Ecomm.dto.RazorpayOrderRequestDTO;
import com.example.Ecomm.dto.RazorpayOrderResponseDTO;
import com.example.Ecomm.dto.RazorpayPaymentCaptureRequestDTO;
import com.example.Ecomm.entitiy.Order;
import com.example.Ecomm.entitiy.Order.OrderStatus;
import com.example.Ecomm.entitiy.Payment;
import com.example.Ecomm.exception.ResourceNotFoundException;
import com.example.Ecomm.repository.OrderRepository;
import com.example.Ecomm.repository.PaymentRepository;
import com.example.Ecomm.service.PaymentService;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    @Transactional
    public PaymentDTO processPayment(Long orderId, PaymentDTO paymentDTO) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "Id", orderId));

        if ("PAID".equals(order.getStatus().name()) || "CANCELLED".equals(order.getStatus().name())) {
            throw new IllegalArgumentException("Order " + orderId + " is already paid or cancelled. Cannot process payment.");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(paymentDTO.getPaymentMethod());
        payment.setAmount(paymentDTO.getAmount());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus("COMPLETED");

        Payment savedPayment = paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        return convertToDTO(savedPayment);
    }
    
    @Override
    public RazorpayOrderResponseDTO createRazorpayOrder(RazorpayOrderRequestDTO requestDTO) throws Exception {
        RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

        long amountInPaise = requestDTO.getAmount();

        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Amount must be a positive value.");
        }

        if (amountInPaise > 99999900) {
            throw new IllegalArgumentException("Amount exceeds Razorpay's maximum allowed limit of ₹9,99,999.00.");
        }

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", requestDTO.getCurrency());
        orderRequest.put("receipt", requestDTO.getReceipt());

        com.razorpay.Order razorpayOrder = razorpay.orders.create(orderRequest);
        
        System.out.println("Razorpay API Response: " + razorpayOrder.toString());


        
        String razorpayId = razorpayOrder.get("id");
        System.out.println("Extracted Razorpay ID: " + razorpayId);
        
        
        RazorpayOrderResponseDTO responseDTO = new RazorpayOrderResponseDTO();
        
        responseDTO.setId(razorpayOrder.get("id").toString());
        responseDTO.setEntity(razorpayOrder.get("entity").toString());

        Object amountObj = razorpayOrder.get("amount");
        if (amountObj instanceof Number) {
            responseDTO.setAmount(((Number) amountObj).longValue());
        } else {
            System.err.println("Unexpected amount type from Razorpay: " + (amountObj != null ? amountObj.getClass().getName() : "null"));
            responseDTO.setAmount(null);
        }

        responseDTO.setCurrency(razorpayOrder.get("currency").toString());
        responseDTO.setReceipt(razorpayOrder.get("receipt").toString());
        responseDTO.setStatus(razorpayOrder.get("status").toString());
        responseDTO.setAttempts(razorpayOrder.get("attempts"));

        Object createdAtObj = razorpayOrder.get("created_at");
        if (createdAtObj instanceof Long) {
            responseDTO.setCreatedAt((Long) createdAtObj);
        } else if (createdAtObj instanceof Date) {
            responseDTO.setCreatedAt(((Date) createdAtObj).getTime() / 1000);
        } else {
            System.err.println("Unexpected type for created_at: " + (createdAtObj != null ? createdAtObj.getClass().getName() : "null"));
            responseDTO.setCreatedAt(null);
        }

        return responseDTO;
    }
    
    @Override
    @Transactional
    public PaymentDTO captureRazorpayPayment(RazorpayPaymentCaptureRequestDTO requestDTO) throws Exception {
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", requestDTO.getRazorpayOrderId());
        options.put("razorpay_payment_id", requestDTO.getRazorpayPaymentId());
        options.put("razorpay_signature", requestDTO.getRazorpaySignature());

        boolean signatureIsValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

        if (!signatureIsValid) {
            throw new IllegalArgumentException("Razorpay signature verification failed. Payment is not authentic.");
        }

        Order order = orderRepository.findById(requestDTO.getInternalOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "Id", requestDTO.getInternalOrderId()));

        if ("PAID".equals(order.getStatus().name())) {
            throw new IllegalArgumentException("Order " + order.getId() + " is already marked as PAID. Duplicate payment attempt.");
        }
        if ("CANCELLED".equals(order.getStatus().name())) {
            throw new IllegalArgumentException("Order " + order.getId() + " is CANCELLED. Cannot capture payment.");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod("RAZORPAY");
        payment.setAmount(requestDTO.getAmount());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus("COMPLETED");

        Payment savedPayment = paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        return convertToDTO(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "Id", paymentId));
        return convertToDTO(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByOrderId(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "Id", orderId));
        return paymentRepository.findByOrder(order).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "Id", paymentId));
        paymentRepository.delete(payment);
    }
    
    @Override
    @Transactional
    public PaymentDTO captureWebhookPayment(JSONObject paymentEntity, Long internalOrderId) throws Exception {
        Order order = orderRepository.findById(internalOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "Id", internalOrderId));

        if ("PAID".equals(order.getStatus().name())) {
            throw new IllegalArgumentException("Order " + order.getId() + " is already marked as PAID. Duplicate payment attempt.");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod("RAZORPAY");
        
        BigDecimal amountInRupees = new BigDecimal(paymentEntity.getLong("amount")).divide(new BigDecimal("100"));
        payment.setAmount(amountInRupees);

        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus("COMPLETED");

        Payment savedPayment = paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        return convertToDTO(savedPayment);
    }

    private PaymentDTO convertToDTO(Payment payment) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setAmount(payment.getAmount());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setStatus(payment.getStatus());
        dto.setOrderId(payment.getOrder() != null ? payment.getOrder().getId() : null);
        return dto;
    }
}