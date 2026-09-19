package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.exception.PayloadKeyMissingException;
import com.pawtrail.extract.domain.model.GoCampingRaw;
import com.pawtrail.extract.support.RawFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoCampingPayloadReaderTest {

    private final GoCampingPayloadReader reader = new GoCampingPayloadReader();

    @Test
    @DisplayName("반려동물 출입과 글 칸 열세 개를 정해진 순서로 읽는다")
    void 어반파크() {
        GoCampingRaw raw = reader.read(RawFixtures.load("gocamping/small-dogs-tooltip"));

        assertThat(raw.animalCmgCl()).isEqualTo("가능(소형견)");
        assertThat(raw.texts().keySet()).containsExactlyElementsOf(GoCampingRaw.TEXT_KEYS);
        assertThat(raw.texts().get("tooltip")).startsWith("소형견(10kg미만) 동반 가능하며");
    }

    @Test
    @DisplayName("반려동물 출입 키가 없으면 실패다")
    @SuppressWarnings("unchecked")
    void 출입_키_없음() {
        Map<String, Object> payload = RawFixtures.load("gocamping/refused");
        ((Map<String, Object>) payload.get("list")).remove("animalCmgCl");

        assertThatThrownBy(() -> reader.read(payload))
                .isInstanceOf(PayloadKeyMissingException.class)
                .hasMessageContaining("animalCmgCl");
    }

    @Test
    @DisplayName("글 칸 키가 없어도 실패다")
    @SuppressWarnings("unchecked")
    void 글_칸_키_없음() {
        Map<String, Object> payload = RawFixtures.load("gocamping/refused");
        ((Map<String, Object>) payload.get("list")).remove("tooltip");

        assertThatThrownBy(() -> reader.read(payload))
                .isInstanceOf(PayloadKeyMissingException.class)
                .hasMessageContaining("tooltip");
    }
}
