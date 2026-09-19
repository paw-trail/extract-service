package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.exception.PayloadKeyMissingException;
import com.pawtrail.extract.domain.model.CultureRaw;
import com.pawtrail.extract.support.RawFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CulturePayloadReaderTest {

    private final CulturePayloadReader reader = new CulturePayloadReader();

    @Test
    @DisplayName("여덟 키를 실물 이름 그대로 읽는다")
    void 경기도미술관() {
        CultureRaw raw = reader.read(RawFixtures.load("culture/outdoor-only"));

        assertThat(raw.category3()).isEqualTo("미술관");
        assertThat(raw.companion()).isEqualTo("Y");
        assertThat(raw.indoor()).isEqualTo("N");
        assertThat(raw.outdoor()).isEqualTo("Y");
        assertThat(raw.petOnly()).isEqualTo("해당없음");
        assertThat(raw.size()).isEqualTo("모두 가능");
        assertThat(raw.fee()).isEqualTo("없음");
        assertThat(raw.restriction()).isEqualTo("야외만 반려동물 동반 가능, 목줄");
    }

    @Test
    @DisplayName("실외 키를 실내 키처럼 띄어 쓴 원문은 조용히 비우지 않고 실패한다")
    @SuppressWarnings("unchecked")
    void 실외_키_띄어쓰기() {
        Map<String, Object> payload = RawFixtures.load("culture/outdoor-only");
        Map<String, Object> list = (Map<String, Object>) payload.get("list");
        list.put("장소(실외) 여부", list.remove("장소(실외)여부"));

        assertThatThrownBy(() -> reader.read(payload))
                .isInstanceOf(PayloadKeyMissingException.class)
                .hasMessageContaining("장소(실외)여부");
    }
}
