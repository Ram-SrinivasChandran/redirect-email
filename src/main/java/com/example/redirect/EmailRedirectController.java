package com.example.redirect;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailRedirectController {

    private final EmailRedirectService emailRedirectService;

    @PostMapping("/receive")
    public ResponseEntity<String> receiveEvent(@RequestBody String body) {
        System.out.println("Event received: " + body);
        emailRedirectService.extractFile(body);
        return ResponseEntity.ok("OK");
    }
}
