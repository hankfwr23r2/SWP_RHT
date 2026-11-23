package com.example.racehorse_transport.service;



public interface MailDeliveryService {

    boolean isConfigured();

    void send(String to, String subject, String text);

    void sendHtml(String to, String subject, String html, String text);
}
