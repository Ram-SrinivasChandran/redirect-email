# 📥 Email Processing with Amazon SES, S3, EventBridge & Java (No Lambda / No SNS)

## 🎯 Objective  
Build a **Java‑based backend** that automatically receives and processes e‑mail using **Amazon SES**, stores each message in **S3**, and lets **EventBridge** trigger your HTTP API.  
*No AWS Lambda, no SNS* — just SES → S3 → EventBridge → Java.

---

## 🗺️ High‑Level Architecture  

![Architecture Diagram](./Untitled%20Diagram.jpg)

---

## ✅ Requirements  

| Area | Requirement |
|------|-------------|
| **AWS** | • Amazon SES (inbound)  <br>• Amazon S3 bucket  <br>• Amazon EventBridge <br>• IAM role/policy |
| **Java Backend** | • Java 17+ <br>• Spring Boot (or plain Servlet) <br>• Maven for deps <br>• Publicly reachable HTTPS endpoint |

---

## 🔧 Step‑by‑Step Setup  

### 1 – Amazon SES  
1. **Verify domain** (e.g. `yourdomain.com`).  
2. **Rule Set** → add rule:  
   * **Recipient** = `example@yourdomain.com`  
   * **Action** = *Deliver to S3 bucket* (`redirect-incoming-emails-bucket`).  
   * Resulting object key = SES Message‑ID **.eml**.

### 2 – S3 Bucket  
*Bucket name shown as* `redirect-incoming-emails-bucket`.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowSESPuts",
      "Effect": "Allow",
      "Principal": { "Service": "ses.amazonaws.com" },
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::redirect-incoming-emails-bucket/*",
      "Condition": {
        "StringEquals": { "AWS:SourceAccount": "YOUR_ACCOUNT_ID" },
        "StringLike":   { "AWS:SourceArn": "arn:aws:ses:*" }
      }
    }
  ]
}
```

* Turn **off** “Block all public access” (or tailor to your security model).  
* **Properties ▸ Event notifications ▸ EventBridge → Enable**.

### 3 – EventBridge  
1. **Connection** → *Authorization = None* (or Basic/Auth if desired).  
2. **API Destination** → HTTPS POST to `https://yourdomain.com/app/email/receive`.  
3. **Rule** (event pattern):

```json
{
  "source": ["aws.s3"],
  "detail-type": ["Object Created"],
  "detail": {
    "bucket": { "name": ["redirect-incoming-emails-bucket"] }
  }
}
```

Target = the API Destination above.

### 4 – Java Backend  

#### Endpoint  
`POST /app/email/receive`

#### Core code  

```java
public void processEmail(String bodyJson) {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(bodyJson);
    String bucket = root.at("/detail/bucket/name").asText();
    String key    = root.at("/detail/object/key").asText();

    File eml = Files.createTempFile("email", ".eml").toFile();
    s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
            .transferTo(new FileOutputStream(eml));

    Session session = Session.getDefaultInstance(new Properties());
    MimeMessage msg = new MimeMessage(session, new FileInputStream(eml));

    String subject = msg.getSubject();
    String from    = ((InternetAddress) msg.getFrom()[0]).getAddress();
    String body    = extractPlainTextBody(msg.getContent());

    System.out.printf("Subject: %s%nFrom: %s%nBody:%n%s%n", subject, from, body);
    extractAttachments(msg.getContent()); // saves to ./attachments
}
```

<details>
<summary>helper methods</summary>

```java
private String extractPlainTextBody(Object content) throws Exception {
    StringBuilder out = new StringBuilder();
    if (content instanceof String s) {
        out.append(s);
    } else if (content instanceof Multipart mp) {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart p = mp.getBodyPart(i);
            if (p.getContent() instanceof String txt &&
                p.getContentType().toLowerCase().contains("text/plain")) {
                out.append(txt); break;
            } else if (p.getContent() instanceof Multipart nested) {
                out.append(extractPlainTextBody(nested));
            }
        }
    }
    return out.toString().trim();
}

private void extractAttachments(Object content) throws Exception {
    if (!(content instanceof Multipart mp)) return;
    File dir = new File("attachments"); dir.mkdirs();
    for (int i = 0; i < mp.getCount(); i++) {
        BodyPart p = mp.getBodyPart(i);
        if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition()) || p.getFileName() != null) {
            try (InputStream in = p.getInputStream();
                 FileOutputStream out = new FileOutputStream(new File(dir, p.getFileName()))) {
                in.transferTo(out);
            }
            System.out.println("Saved attachment: " + p.getFileName());
        }
    }
}
```
</details>

---

## 📦 Maven Dependencies  

```xml
<dependency>
  <groupId>com.sun.mail</groupId>
  <artifactId>jakarta.mail</artifactId>
  <version>2.0.1</version>
</dependency>
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
  <version>2.25.10</version>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.17.1</version>
</dependency>
```

---

## 🏁 Sample Output  

```
Subject: Testing SES → EventBridge
From: sender@example.com
Body:
Hello, this is a test.

Saved attachment: file1.png
Saved attachment: invoice.pdf
```

---

## 🔮 Future Enhancements  

- Upload attachments to a second S3 bucket  
- Persist metadata in PostgreSQL/DynamoDB  
- Convert HTML‑only emails to text (e.g., with jsoup)  
- Add CloudWatch metrics / structured logging  

---

## 📌 Notes  

- **No AWS Lambda or SNS** used — pure EventBridge → HTTP.  
- Works with public or private endpoints (use VPC Link / API GW if internal).  
- Easily extendable to multi‑tenant or multi‑domain setups.
