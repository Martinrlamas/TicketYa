package com.ticketya.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 2.2 Unicidad e impredecibilidad del QR. */
class QrTokenGeneratorTest {

    private final QrTokenGenerator generator = new QrTokenGenerator(new SecureRandom());

    @Test
    void tenThousandTokensHaveNoCollisions() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tokens.add(generator.generate());
        }
        assertThat(tokens).hasSize(10_000);
    }

    @Test
    void tokenIs32BytesInUnpaddedBase64Url() {
        String token = generator.generate();

        assertThat(token).hasSize(43);                                  // ceil(32 * 8 / 6), sin '='
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(java.util.Base64.getUrlDecoder().decode(token)).hasSize(QrTokenGenerator.TOKEN_BYTES);
    }

    @Test
    void tokensAreNotSequential() {
        // dos tokens consecutivos no comparten prefijo: no hay contador ni dato derivable
        String a = generator.generate();
        String b = generator.generate();
        assertThat(a.substring(0, 8)).isNotEqualTo(b.substring(0, 8));
    }
}
