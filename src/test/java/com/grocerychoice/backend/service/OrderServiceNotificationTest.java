package com.grocerychoice.backend.service;

import com.grocerychoice.backend.dto.CreateOrderRequest;
import com.grocerychoice.backend.dto.OrderItemRequest;
import com.grocerychoice.backend.dto.OrderResponse;
import com.grocerychoice.backend.entity.*;
import com.grocerychoice.backend.repository.AddressRepository;
import com.grocerychoice.backend.repository.OrderRepository;
import com.grocerychoice.backend.repository.ProductRepository;
import com.grocerychoice.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceNotificationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private NotificationService notificationService;

    private OrderService orderService;

    private User sampleCustomer;
    private Product sampleProduct;
    private Address sampleAddress;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                productRepository,
                userRepository,
                addressRepository,
                notificationService
        );

        sampleCustomer = new User();
        sampleCustomer.setId(1L);
        sampleCustomer.setFullName("Priya Patel");
        sampleCustomer.setEmail("priya.patel@example.com");
        sampleCustomer.setPhone("+91 91234 56789");
        sampleCustomer.setRole(Role.CUSTOMER);

        sampleProduct = new Product();
        sampleProduct.setId(10L);
        sampleProduct.setName("Farm Fresh Milk 1L");
        sampleProduct.setMrp(new BigDecimal("70.00"));
        sampleProduct.setSellingPrice(new BigDecimal("65.00"));
        sampleProduct.setStockQuantity(50);
        sampleProduct.setActive(true);

        sampleAddress = new Address();
        sampleAddress.setId(20L);
        sampleAddress.setUser(sampleCustomer);
        sampleAddress.setAddressLine1("Flat 402, Sunshine Heights");
        sampleAddress.setCity("Mumbai");
        sampleAddress.setState("Maharashtra");
        sampleAddress.setPostalCode("400001");
        sampleAddress.setIsDefault(true);
    }

    @Test
    @DisplayName("Rule 7: COD order creation confirms order and triggers customer notification")
    void testCreateOrder_CodTriggersNotification() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setAddressId(20L);
        request.setPaymentMethod("Cash on Delivery");
        request.setDeliverySlot("Standard Delivery (30-45 mins)");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(10L);
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(addressRepository.findById(20L)).thenReturn(Optional.of(sampleAddress));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(501L);
            return o;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(501L);
        assertThat(response.getPaymentMethod()).isEqualTo("Cash on Delivery");
        verify(notificationService, times(1)).sendOrderConfirmation(any(Order.class));
    }

    @Test
    @DisplayName("Rule 5 prerequisite: Online/Razorpay order does NOT trigger confirmation notification at creation")
    void testCreateOrder_OnlinePaymentDoesNotTriggerNotificationAtCreation() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setAddressId(20L);
        request.setPaymentMethod("Online (Razorpay)");
        request.setDeliverySlot("Standard Delivery (30-45 mins)");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(10L);
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(addressRepository.findById(20L)).thenReturn(Optional.of(sampleAddress));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(502L);
            return o;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(502L);
        // Confirmation notification must NOT be sent before Razorpay signature verification
        verify(notificationService, never()).sendOrderConfirmation(any(Order.class));
    }

    @Test
    @DisplayName("Rule 7 & 8/9: Notification dispatch failure does NOT rollback or fail COD order creation")
    void testCreateOrder_NotificationFailureDoesNotFailCodOrder() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(1L);
        request.setAddressId(20L);
        request.setPaymentMethod("COD");
        request.setDeliverySlot("Standard Delivery (30-45 mins)");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(10L);
        itemRequest.setQuantity(1);
        request.setItems(List.of(itemRequest));

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(addressRepository.findById(20L)).thenReturn(Optional.of(sampleAddress));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(503L);
            return o;
        });

        // Notification service throws unexpected exception
        doThrow(new RuntimeException("SMS gateway network timeout"))
                .when(notificationService).sendOrderConfirmation(any(Order.class));

        OrderResponse response = assertDoesNotThrow(() -> orderService.createOrder(request));

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(503L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PLACED);
        verify(notificationService, times(1)).sendOrderConfirmation(any(Order.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }
}
