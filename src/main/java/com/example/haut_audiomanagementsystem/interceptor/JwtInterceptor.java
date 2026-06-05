package com.example.haut_audiomanagementsystem.interceptor;

import com.example.haut_audiomanagementsystem.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
                jwtUtil.parseToken(token.substring(7));
                return true; // Token 合法，放行
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