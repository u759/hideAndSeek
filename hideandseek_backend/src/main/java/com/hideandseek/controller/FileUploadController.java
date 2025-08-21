package com.hideandseek.controller;

import com.hideandseek.service.ClueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    // Optional override via configuration; if empty, we resolve a safe default at runtime
    @Value("${uploads.selfies.dir:}")
    private String configuredUploadDir;

    private Path resolveUploadDir() {
        // Prefer explicit configuration
        if (configuredUploadDir != null && !configuredUploadDir.isBlank()) {
            return Paths.get(configuredUploadDir).toAbsolutePath().normalize();
        }
        // WildFly/JBoss: use server data dir if available
        String dataDir = System.getProperty("jboss.server.data.dir");
        Path base;
        if (dataDir != null && !dataDir.isBlank()) {
            base = Paths.get(dataDir);
        } else {
            // Fallback to process working dir to avoid writing to filesystem root
            base = Paths.get(System.getProperty("user.dir", "."));
        }
        return base.resolve("hideandseek").resolve("uploads").resolve("selfies").toAbsolutePath().normalize();
    }

    @Autowired
    private ClueService clueService;

    @PostMapping("/selfie")
    public ResponseEntity<?> uploadSelfie(
            @RequestParam("file") MultipartFile file,
            @RequestParam("requestId") String requestId,
            @RequestParam("teamId") String teamId) {
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "File must be an image"));
            }

            // Resolve a writable upload directory and ensure it exists
            Path uploadPath = resolveUploadDir();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(filename).normalize();

            // Save file
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Generate absolute URL for the file
            String baseUrl = "";
            try {
                // Best-effort: infer host from X-Forwarded-* headers or request context
                baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            } catch (Exception ignored) {}
            String relativePath = "/api/uploads/files/" + filename;
            String fileUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl + relativePath : relativePath;

            // Submit the photo URL as the response to the clue request
            var result = clueService.respondToClueRequest(requestId, teamId, fileUrl);
            result.put("fileUrl", fileUrl);
            result.put("filename", filename);
            result.put("relativePath", relativePath);

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("Error uploading file", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing selfie upload", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process selfie upload"));
        }
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<?> getFile(@PathVariable String filename) {
        try {
            // Prevent path traversal by normalizing to a basename
            String safeName = Paths.get(filename).getFileName().toString();
            Path filePath = resolveUploadDir().resolve(safeName).normalize();
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            // Return the file content
            byte[] fileContent = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .body(fileContent);

        } catch (IOException e) {
            log.error("Error retrieving file", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
