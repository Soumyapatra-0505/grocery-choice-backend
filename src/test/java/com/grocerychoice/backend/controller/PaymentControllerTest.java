package com.grocerychoice.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocerychoice.backend.dto.CreatePaymentOrderRequest;
import com.grocerychoice.backend.dto.PaymentVerificationRequest;
import com.grocerychoice.backend.dto.PaymentVerificationResponse;
import com.grocerychoice.backend.dto.RazorpayOrderResponse;
import com.grocerychoice.backend.entity.PaymentStatus;
import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.exception.InvalidDataException;
import com.grocerychoice.backend.security.UserPrincipal;
import com.grocerychoice.backend.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testPrincipal = new UserPrincipal(10L, "customer@example.com", "+91 99999 88888", "Customer User", Role.CUSTOMER);

        // Custom resolver to mock @AuthenticationPrincipal UserPrincipal
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String authHeader = webRequest.getHeader("X-Mock-No-Auth");
                if ("true".equalsIgnoreCase(authHeader)) {
                    return null;
                }
                return testPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("POST /api/payments/create-order returns 200 with Razorpay order details")
    void testCreatePaymentOrder_Success() throws Exception {
        RazorpayOrderResponse mockResponse = new RazorpayOrderResponse(
                "order_rzp_mock_123",
                "rzp_test_mockKey",
                49900L,
                "INR",
                101L
        );

        when(paymentService.createPaymentOrder(eq(101L), any(UserPrincipal.class)))
                .thenReturn(mockResponse);

        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(101L);

        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpayOrderId").value("order_rzp_mock_123"))
                .andExpect(jsonPath("$.razorpayKeyId").value("rzp_test_mockKey"))
                .andExpect(jsonPath("$.amount").value(49900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.orderId").value(101));
    }

    @Test
    @DisplayName("POST /api/payments/create-order returns 401 Unauthorized if principal is missing")
    void testCreatePaymentOrder_Unauthorized() throws Exception {
        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(101L);

        mockMvc.perform(post("/api/payments/create-order")
                        .header("X-Mock-No-Auth", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/payments/verify returns 200 with verified PAID status")
    void testVerifyPayment_Success() throws Exception {
        PaymentVerificationResponse mockResponse = new PaymentVerificationResponse(
                true,
                "Payment verified successfully",
                101L,
                "GC-2026-00101",
                PaymentStatus.PAID
        );

        when(paymentService.verifyPayment(any(PaymentVerificationRequest.class), any(UserPrincipal.class)))
                .thenReturn(mockResponse);

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "valid_sig_789"
        );

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.orderId").value(101))
                .andExpect(jsonPath("$.orderNumber").value("GC-2026-00101"));
    }

    @Test
    @DisplayName("POST /api/payments/verify returns 401 Unauthorized if principal is missing")
    void testVerifyPayment_Unauthorized() throws Exception {
        PaymentVerificationRequest request = new PaymentVerificationRequest(
                101L,
                "order_rzp_mock_123",
                "pay_mock_456",
                "valid_sig_789"
        );

        mockMvc.perform(post("/api/payments/verify")
                        .header("X-Mock-No-Auth", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/payments/fail returns 200 with FAILED payment status")
    void testRecordPaymentFailure_Success() throws Exception {
        PaymentVerificationResponse mockResponse = new PaymentVerificationResponse(
                false,
                "Payment was declined or cancelled",
                101L,
                "GC-2026-00101",
                PaymentStatus.FAILED
        );

        when(paymentService.handlePaymentFailure(eq(101L), any(String.class), any(UserPrincipal.class)))
                .thenReturn(mockResponse);

        CreatePaymentOrderRequest request = new CreatePaymentOrderRequest(101L);

        mockMvc.perform(post("/api/payments/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.orderId").value(101));
    }
}
