package com.selflearning.authservice.auth.request;

public record TokenValidateRequest(
        /**
         * JWT access token，可直接传 token，也可传 Bearer token。
         */
        String token
) {
}
