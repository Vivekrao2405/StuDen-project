package com.studen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final RequestMatcher publicEndpoints;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService,
            RequestMatcher publicEndpoints) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            if (jwtService.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = jwtService.extractEmail(token);

                // A validly-signed, unexpired token can still refer to an account that no longer
                // exists (deleted after the token was issued, before it expired naturally) —
                // treated as simply unauthenticated rather than letting the lookup failure escape
                // this filter as an uncaught exception, which would otherwise surface as a raw
                // 500 instead of the normal 401 an anonymous request gets.
                UserDetails userDetails;
                try {
                    userDetails = userDetailsService.loadUserByUsername(email);
                } catch (UsernameNotFoundException ex) {
                    userDetails = null;
                }

                if (userDetails != null && userDetails.isEnabled()) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
