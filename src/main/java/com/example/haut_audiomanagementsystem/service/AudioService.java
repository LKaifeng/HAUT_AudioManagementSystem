package com.example.haut_audiomanagementsystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.haut_audiomanagementsystem.entity.AudioAsset;
import com.example.haut_audiomanagementsystem.mapper.AudioAssetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;

@Service
public class AudioService {

    @Resource
    private AudioAssetMapper audioAssetMapper;

    @Value("${app.storage.path}")
    private String storagePath;

    /**
     * 上传音频
     */
    @Transactional(rollbackFor = Exception.class)
    public void uploadAudio(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("文件为空");

        // 1. 生成唯一文件名 UUID.mp3
        String originalName = file.getOriginalFilename();
        String extension = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : ".mp3";
        String uuidName = UUID.randomUUID().toString() + extension;
        
        // 2. 确保目录存在
        File dir = new File(storagePath);
        if (!dir.exists()) dir.mkdirs();

        // 3. 保存物理文件
        Path filePath = Paths.get(storagePath, uuidName);
        Files.write(filePath, file.getBytes());

        // 4. 写入数据库
        AudioAsset asset = new AudioAsset();
        asset.setFileName(originalName);
        asset.setFilePath(filePath.toString());
        asset.setFileSize(file.getSize());
        asset.setCreateTime(new Date());
        asset.setStatus(1);
        // TODO: 计算 MD5 hash_code
        
        audioAssetMapper.insert(asset);
    }

    /**
     * 分页查询 (按创建时间降序)
     */
    public Page<AudioAsset> listAudios(int page, int size) {
        Page<AudioAsset> pageInfo = new Page<>(page, size);
        QueryWrapper<AudioAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)
               .orderByDesc("create_time");
        return audioAssetMapper.selectPage(pageInfo, wrapper);
    }

    /**
     * 删除音频 (物理+逻辑)
     */
    @Transactional
    public void deleteAudio(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) return;

        // 1. 删除物理文件
        File file = new File(asset.getFilePath());
        if (file.exists()) {
            file.delete();
        }

        // 2. 删除数据库记录
        audioAssetMapper.deleteById(id);
    }
    
    /**
     * 获取文件流 (用于播放)
     */
    public File getAudioFile(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) return null;
        return new File(asset.getFilePath());
    }
}