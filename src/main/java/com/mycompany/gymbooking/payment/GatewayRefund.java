package com.mycompany.gymbooking.payment;

/** @param id the provider's id for the refund, e.g. "re_3Q1x..." */
public record GatewayRefund(String id, String status) {
}
