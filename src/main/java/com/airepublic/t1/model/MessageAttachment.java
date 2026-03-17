package com.airepublic.t1.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a file attachment associated with a conversation message.
 * Supports images, documents, and other media types for analysis by AI models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {
    /**
     * Unique identifier for this attachment
     */
    private String id;

    /**
     * Original filename of the uploaded file
     */
    private String filename;

    /**
     * MIME type of the file (e.g., image/png, image/jpeg, application/pdf)
     */
    private String mimeType;

    /**
     * Base64-encoded content of the file
     */
    private String contentBase64;

    /**
     * File size in bytes
     */
    private Long fileSize;

    /**
     * Timestamp when the file was uploaded
     */
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /**
     * Optional description or caption for the attachment
     */
    private String description;

    /**
     * Checks if this attachment is an image type
     */
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Checks if this attachment is a document type
     */
    public boolean isDocument() {
        return mimeType != null && (
            mimeType.equals("application/pdf") ||
            mimeType.startsWith("application/vnd.") ||
            mimeType.equals("text/plain")
        );
    }
}
