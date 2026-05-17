package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

@RestController
public class UploadFileController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping("/uploads/{year}/{month}/{filename:.+}")
    public ResponseEntity<Resource> getUploadedFile(
        @PathVariable String year,
        @PathVariable String month,
        @PathVariable String filename
    ) throws Exception {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(year).resolve(month).resolve(filename).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw BizException.badRequest("图片不存在");
        }

        Resource resource = new UrlResource(target.toUri());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .contentType(mediaType(filename))
            .body(resource);
    }

    private MediaType mediaType(String filename) {
        String value = filename.toLowerCase(Locale.ROOT);
        if (value.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (value.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (value.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
