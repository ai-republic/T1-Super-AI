package com.airepublic.t1.service;

import com.airepublic.t1.model.MessageAttachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for handling file attachments in chat messages.
 * Converts uploaded files to MessageAttachment objects with base64-encoded content.
 */
@Slf4j
@Service
public class FileAttachmentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final String[] SUPPORTED_IMAGE_TYPES = {
        "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    };
    private static final String[] SUPPORTED_DOCUMENT_TYPES = {
        "application/pdf", "text/plain"
    };

    /**
     * Converts a MultipartFile to a MessageAttachment with base64-encoded content
     */
    public MessageAttachment processFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        String mimeType = file.getContentType();
        if (mimeType == null) {
            throw new IllegalArgumentException("File must have a valid MIME type");
        }

        // Validate file type
        if (!isSupported(mimeType)) {
            throw new IllegalArgumentException(
                String.format("Unsupported file type: %s. Supported types: images (PNG, JPEG, GIF, WebP), PDF, text", mimeType)
            );
        }

        // Convert to base64
        byte[] fileBytes = file.getBytes();
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);

        return MessageAttachment.builder()
            .id(UUID.randomUUID().toString())
            .filename(file.getOriginalFilename())
            .mimeType(mimeType)
            .contentBase64(base64Content)
            .fileSize(file.getSize())
            .build();
    }

    /**
     * Checks if the given MIME type is supported
     */
    public boolean isSupported(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        // Check image types
        for (String type : SUPPORTED_IMAGE_TYPES) {
            if (mimeType.equals(type)) {
                return true;
            }
        }

        // Check document types
        for (String type : SUPPORTED_DOCUMENT_TYPES) {
            if (mimeType.equals(type)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validates that at least one image attachment exists for vision tasks
     */
    public boolean hasImageAttachment(MessageAttachment attachment) {
        return attachment != null && attachment.isImage();
    }
}
