package com.studen.auth;

/**
 * Pairs the response body sent to the client with the raw refresh token value, which the
 * controller places directly into an HttpOnly cookie rather than the JSON body — it must never
 * be exposed to JavaScript.
 */
public record AuthResult(AuthResponse authResponse, String refreshToken) {
}
