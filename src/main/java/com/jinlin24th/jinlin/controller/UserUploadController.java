package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/user/upload")
public class UserUploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024L; // C 端限制 2MB

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.public-url:}")
    private String publicUrl;

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择图片文件");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw BizException.badRequest("图片不能超过 2MB");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BizException.badRequest("仅支持 jpg、png、webp、gif 图片");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        Path targetDir = Path.of(uploadDir).toAbsolutePath().normalize().resolve(datePath);
        Files.createDirectories(targetDir);

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = targetDir.resolve(filename).normalize();
        file.transferTo(target);

        String path = "/uploads/" + datePath + "/" + filename;
        String url = publicBaseUrl(request) + path;
        return Result.success(Map.of("url", url));
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String publicBaseUrl(HttpServletRequest request) {
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.replaceFirst("/+$", "");
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }
        return proto + "://" + host;
    }
}
