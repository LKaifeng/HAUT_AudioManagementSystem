package com.example.haut_audiomanagementsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.haut_audiomanagementsystem.entity.AudioAsset;
import com.example.haut_audiomanagementsystem.service.AudioService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class AudioController {

    @Autowired
    private AudioService audioService;

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");

            if (roleLevel == null || (roleLevel != 0 && roleLevel != 1)) {
                return ResponseEntity.status(403).body("权限不足：只有管理员和操作员可以上传音频");
            }

            audioService.uploadAudio(file);
            return ResponseEntity.ok("上传成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Page<AudioAsset>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(audioService.listAudios(page, size, keyword));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable String id,
            @RequestBody Map<String, String> updateData,
            HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");

            System.out.println("[AudioController] 更新请求 - ID: " + id + ", 角色等级: " + roleLevel + ", 新文件名: "
                    + updateData.get("fileName"));

            if (roleLevel == null || roleLevel != 0) {
                System.out.println("[AudioController] 权限不足 - 角色等级: " + roleLevel);
                return ResponseEntity.status(403).body("权限不足：只有管理员可以修改音频");
            }

            String newFileName = updateData.get("fileName");
            if (newFileName == null || newFileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("文件名不能为空");
            }

            audioService.updateAudioName(Long.parseLong(id), newFileName.trim());
            System.out.println("[AudioController] 更新成功 - ID: " + id);
            return ResponseEntity.ok("修改成功");
        } catch (Exception e) {
            System.err.println("[AudioController] 更新失败 - ID: " + id + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("修改失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable String id,
            HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");

            System.out.println("[AudioController] 删除请求 - ID: " + id + ", 角色等级: " + roleLevel);

            if (roleLevel == null || roleLevel != 0) {
                System.out.println("[AudioController] 权限不足 - 角色等级: " + roleLevel);
                return ResponseEntity.status(403).body("权限不足：只有管理员可以删除音频");
            }

            audioService.deleteAudio(Long.parseLong(id));
            System.out.println("[AudioController] 删除成功 - ID: " + id);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            System.err.println("[AudioController] 删除失败 - ID: " + id + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupInvalidRecords(HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以执行清理操作");
            return ResponseEntity.status(403).body(error);
        }

        int cleanedCount = audioService.cleanupInvalidRecords();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("cleanedCount", cleanedCount);
        result.put("msg", "已清理 " + cleanedCount + " 条无效记录");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> stream(@PathVariable Long id) {
        File file = audioService.getAudioFile(id);
        if (file == null) {
            System.err.println("音频文件不存在或状态异常，ID: " + id);
            return ResponseEntity.notFound().build();
        }

        if (!file.exists()) {
            System.err.println("物理文件不存在，ID: " + id + ", 路径: " + file.getAbsolutePath());
            return ResponseEntity.status(404).body(null);
        }

        if (!file.canRead()) {
            System.err.println("文件不可读，ID: " + id + ", 路径: " + file.getAbsolutePath());
            return ResponseEntity.status(403).body(null);
        }

        Resource resource = new FileSystemResource(file);
        String contentType = getContentType(file.getName());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
                .body(resource);
    }

    @GetMapping("/check/{id}")
    public ResponseEntity<Map<String, Object>> checkAudio(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        AudioAsset asset = audioService.getAudioAssetById(id);
        if (asset == null) {
            result.put("exists", false);
            result.put("reason", "数据库记录不存在");
            return ResponseEntity.ok(result);
        }

        File file = new File(asset.getFilePath());
        if (!file.exists()) {
            result.put("exists", false);
            result.put("reason", "物理文件不存在");
            result.put("filePath", asset.getFilePath());
            return ResponseEntity.ok(result);
        }

        result.put("exists", true);
        result.put("fileName", asset.getFileName());
        result.put("fileSize", file.length());
        result.put("filePath", asset.getFilePath());
        return ResponseEntity.ok(result);
    }

    private String getContentType(String fileName) {
        if (fileName.toLowerCase().endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.toLowerCase().endsWith(".wav")) {
            return "audio/wav";
        }
        return "application/octet-stream";
    }
}