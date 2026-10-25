package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.service.CoffeePaymentService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import okhttp3.Headers;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/coffee")
@RequiredArgsConstructor
public class CoffeePaymentNotifyController {
    private final CoffeePaymentService payment;

    @PostMapping("/payment/notify")
    public Map<String, String> payment(HttpServletRequest request, @RequestBody String body) {
        payment.paymentNotify(headers(request), body);
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    @PostMapping("/refund/notify")
    public Map<String, String> refund(HttpServletRequest request, @RequestBody String body) {
        payment.refundNotify(headers(request), body);
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    private Headers headers(HttpServletRequest r) {
        Headers.Builder h = new Headers.Builder();
        for (String key :
                List.of(
                        "Wechatpay-Timestamp",
                        "Wechatpay-Nonce",
                        "Wechatpay-Serial",
                        "Wechatpay-Signature")) {
            String value = r.getHeader(key);
            if (value != null) h.add(key, value);
        }
        return h.build();
    }
}
