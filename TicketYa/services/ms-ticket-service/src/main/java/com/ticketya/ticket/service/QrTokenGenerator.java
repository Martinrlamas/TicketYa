package com.ticketya.ticket.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Token opaco de 32 bytes aleatorios (SecureRandom) en base64url sin padding (43 caracteres).
 * No codifica ningún dato de negocio: no se puede derivar el evento, asiento ni cliente. 256 bits de
 * entropía hacen impracticable tanto una colisión como adivinar un QR; el UNIQUE de la base es la red final.
 */
@Component
public class QrTokenGenerator {

    static final int TOKEN_BYTES = 32;

    private final SecureRandom random;

    public QrTokenGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
