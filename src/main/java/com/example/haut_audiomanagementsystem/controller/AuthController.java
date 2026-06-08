package com.example.haut_audiomanagementsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.haut_audiomanagementsystem.entity.SysUser;
import com.example.haut_audiomanagementsystem.mapper.SysUserMapper;
import com.example.haut_audiomanagementsystem.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 认证控制器，提供登录接口，返回 JWT token 和用户角色信息
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username).eq("password", password);
        SysUser user = userMapper.selectOne(wrapper);

        if (user != null) {
            String token = jwtUtil.generateToken(user.getUsername(), user.getRoleLevel());
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("token", token);
            result.put("roleLevel", user.getRoleLevel());
            return result;
        }

        Map<String, Object> error = new HashMap<>();
        error.put("code", 401);
        error.put("msg", "用户名或密码错误");
        return error;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> registerData) {
        String username = registerData.get("username");
        String password = registerData.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名和密码不能为空");
            return error;
        }

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username.trim());
        SysUser existUser = userMapper.selectOne(wrapper);

        if (existUser != null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名已存在");
            return error;
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(username.trim());
        newUser.setPassword(password);
        newUser.setRoleLevel(1);

        userMapper.insert(newUser);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "注册成功");
        return result;
    }

    @GetMapping("/users")
    public Map<String, Object> getUsers(HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以查看用户列表");
            return error;
        }

        List<SysUser> users = userMapper.selectList(null);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("users", users);
        return result;
    }

    @PostMapping("/users")
    public Map<String, Object> addUser(@RequestBody Map<String, Object> userData, HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以添加用户");
            return error;
        }

        String username = (String) userData.get("username");
        String password = (String) userData.get("password");
        Integer role = userData.get("role") != null ? ((Number) userData.get("role")).intValue() : 1;

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名和密码不能为空");
            return error;
        }

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username.trim());
        SysUser existUser = userMapper.selectOne(wrapper);

        if (existUser != null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名已存在");
            return error;
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(username.trim());
        newUser.setPassword(password);
        newUser.setRoleLevel(role);

        userMapper.insert(newUser);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "添加成功");
        return result;
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Integer id,
            @RequestBody Map<String, Object> userData,
            HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以修改用户");
            return error;
        }

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "用户不存在");
            return error;
        }

        if (userData.containsKey("password")) {
            String newPassword = (String) userData.get("password");
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPassword(newPassword);
            }
        }

        if (userData.containsKey("roleLevel")) {
            user.setRoleLevel(((Number) userData.get("roleLevel")).intValue());
        }

        userMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "修改成功");
        return result;
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Integer id, HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以删除用户");
            return error;
        }

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "用户不存在");
            return error;
        }

        userMapper.deleteById(id);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "删除成功");
        return result;
    }
}