package com.example.redirect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class EmailRedirectService {

    private final S3Client s3Client = S3Client.builder().region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create()).build();

    public void extractFile(String bodyJson) {
        try {
            // Step 1: Parse S3 EventBridge JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(bodyJson);
            String bucket = json.at("/detail/bucket/name").asText();
            String key = json.at("/detail/object/key").asText();

            // Step 2: Download .eml file from S3
            File emlFile = Files.createTempFile("email", ".eml").toFile();
            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();

            try (ResponseInputStream<?> s3Object = s3Client.getObject(getObjectRequest);
                    FileOutputStream out = new FileOutputStream(emlFile)) {
                s3Object.transferTo(out);
            }

            // Step 3: Parse the email using Jakarta Mail
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);

            try (InputStream source = new FileInputStream(emlFile)) {
                MimeMessage message = new MimeMessage(session, source);

                // ✅ Extract Subject & From
                String subject = message.getSubject();
                String from = ((InternetAddress) message.getFrom()[0]).getAddress();

                Object content = message.getContent();
                String body = extractPlainTextBody(content);

                System.out.println("Subject: " + subject);
                System.out.println("From: " + from);
                System.out.println("Body:\n" + body);

                // ✅ Extract Attachments
                extractAttachments(content);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error processing email", e);
        }

    }

    // ✅ Only extract plain text, not HTML
    private String extractPlainTextBody(Object content) throws Exception {
        StringBuilder plainText = new StringBuilder();

        if (content instanceof String str) {
            plainText.append(str); // fallback if no multipart
        } else if (content instanceof Multipart multipart) {
            extractPlainText(multipart, plainText);
        }

        return plainText.toString().trim();
    }

    private void extractPlainText(Multipart multipart, StringBuilder plainText) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object partContent = part.getContent();

            if (partContent instanceof String strContent) {
                String contentType = part.getContentType().toLowerCase();
                if (contentType.contains("text/plain")) {
                    plainText.append(strContent).append("\n");
                    return; // ✅ Stop at first plain text found
                }

            } else if (partContent instanceof Multipart nestedMultipart) {
                extractPlainText(nestedMultipart, plainText); // recursive
            }

        }

    }

    // ✅ Extract and save attachments
    private void extractAttachments(Object content) throws Exception {
        if (!(content instanceof Multipart multipart))
            return;

        File attachmentDir = new File("attachments");
        if (!attachmentDir.exists())
            attachmentDir.mkdirs();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) || bodyPart.getFileName() != null) {

                String filename = bodyPart.getFileName();
                File file = new File(attachmentDir, filename);

                try (InputStream is = bodyPart.getInputStream(); FileOutputStream fos = new FileOutputStream(file)) {

                    byte[] buf = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buf)) != -1) {
                        fos.write(buf, 0, bytesRead);
                    }

                    System.out.println("Saved attachment: " + filename);
                }

            }

        }

    }
}
