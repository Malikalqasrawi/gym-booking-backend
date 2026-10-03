package com.mycompany.gymbooking.security;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The network address (IP) of the client making the current request. */
public final class ClientAddress {

    /** Used outside a web request, e.g. when a test calls a service directly. */
    public static final String NONE = "none";

    private ClientAddress() {
    }

    /**
     * Like the rate limits, the socket address and not X-Forwarded-For, which clients can set to
     * anything. Behind a proxy, server.forward-headers-strategy=native makes this the real client.
     */
    public static String current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRemoteAddr();
        }
        return NONE;
    }
}
