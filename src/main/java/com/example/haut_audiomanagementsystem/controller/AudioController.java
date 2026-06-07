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
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(audioService.listAudios(page, size));
    }
    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id,
                                        @RequestBody Map<String, String> updateData,
                                        HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");
            
            if (roleLevel == null || roleLevel != 0) {
                return ResponseEntity.status(403).body("权限不足：只有管理员可以修改音频");
            }
            
            String newFileName = updateData.get("fileName");
            if (newFileName == null || newFileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("文件名不能为空");
            }
            
            audioService.updateAudioName(id, newFileName.trim());
            return ResponseEntity.ok("修改成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("修改失败: " + e.getMessage());
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id,
                                         HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");
            
            if (roleLevel == null || roleLevel != 0) {
                return ResponseEntity.status(403).body("权限不足：只有管理员可以删除音频");
            }
            
            audioService.deleteAudio(id);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("删除失败");
        }
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> stream(@PathVariable Long id) {
        File file = audioService.getAudioFile(id);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
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
                .body(resource);
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