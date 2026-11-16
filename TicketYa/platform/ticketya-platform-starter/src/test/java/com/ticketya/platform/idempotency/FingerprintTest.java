package com.ticketya.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprintTest {

    @Test
    void sha256IsHexAndStable() {
        assertThat(Fingerprint.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void differentInputsGiveDifferentFingerprints() {
        assertThat(Fingerprint.sha256("a|1")).isNotEqualTo(Fingerprint.sha256("a|2"));
    }
}
