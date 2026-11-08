package com.api.crud.services;

import com.api.crud.exceptions.ExternalServiceException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public boolean sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom("magosh90@gmail.com");
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            throw new ExternalServiceException(
                    "No se pudo enviar el correo. Intente nuevamente mas tarde.", exception);
        }
    }
}
