package com.example.haut_audiomanagementsystem.interceptor;

import com.example.haut_audiomanagementsystem.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(token.substring(7));
                request.setAttribute("claims", claims);
                request.setAttribute("roleLevel", claims.get("roleLevel", Integer.class));
                return true;
            } catch (Exception e) {
                response.setStatus(401);
                response.getWriter().write("Invalid Token");
                return false;
            }
        }
        response.setStatus(401);
        response.getWriter().write("Missing Token");
        return false;
    }
}