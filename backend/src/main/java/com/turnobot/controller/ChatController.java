package com.turnobot.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turnobot.chat.ChatEngine;
import com.turnobot.dto.ChatRequest;
import com.turnobot.dto.ChatRespuesta;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatEngine chat;

    // Spring inyecta la implementación disponible de ChatEngine (hoy, la de reglas)
    public ChatController(ChatEngine chat) {
        this.chat = chat;
    }

    @PostMapping
    public ChatRespuesta enviar(@Valid @RequestBody ChatRequest pedido) {
        return chat.responder(pedido.sesionId(), pedido.mensaje());
    }

    @GetMapping("/modo")
    public Map<String, String> modo() {
        return Map.of("modo", chat.modo());
    }
}
