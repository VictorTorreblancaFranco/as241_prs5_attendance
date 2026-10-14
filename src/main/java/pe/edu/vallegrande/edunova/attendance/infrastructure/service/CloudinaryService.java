package pe.edu.vallegrande.edunova.attendance.infrastructure.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public Mono<String> uploadFile(byte[] fileBytes, String filename) {
        return Mono.fromCallable(() -> {
            try {
                Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, 
                    ObjectUtils.asMap(
                        "folder", "attendance_justifications",
                        "public_id", System.currentTimeMillis() + "_" + filename.replaceAll("\\s+", "_"),
                        "resource_type", "auto",
                        "allowed_formats", new String[]{"jpg", "jpeg", "png", "pdf"}
                    ));
                return uploadResult.get("secure_url").toString();
            } catch (IOException e) {
                log.error("Error uploading file to Cloudinary: {}", e.getMessage());
                throw new RuntimeException("Error al subir el archivo: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> deleteFile(String publicId) {
        return Mono.fromCallable(() -> {
            try {
                Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                return result.get("result").toString();
            } catch (IOException e) {
                log.error("Error deleting file from Cloudinary: {}", e.getMessage());
                return "error";
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
