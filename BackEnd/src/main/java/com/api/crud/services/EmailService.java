package com.api.crud.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    @Autowired
    private JavaMailSender mailSender;

    public boolean sendEmail(String to, String subject, String body) {
        boolean response = false;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("magosh90@gmail.com");

            mailSender.send(message);
            response = true;
        } catch (Exception e) {
            System.out.println("Error sending email: " + e.getMessage());
        }
        return response;
    }
}