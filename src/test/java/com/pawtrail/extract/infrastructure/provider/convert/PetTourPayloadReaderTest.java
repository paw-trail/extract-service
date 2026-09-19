package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.exception.PayloadKeyMissingException;
import com.pawtrail.extract.domain.model.PetTourRaw;
import com.pawtrail.extract.support.RawFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PetTourPayloadReaderTest {

    private final PetTourPayloadReader reader = new PetTourPayloadReader();

    @Test
    @DisplayName("petTour 블록의 다섯 칸을 읽고 줄바꿈은 그대로 둔다")
    void 익선동() {
        PetTourRaw raw = reader.read(RawFixtures.load("pet-tour/ikseondong"));

        assertThat(raw.acmpyTypeCd()).isEqualTo("일부구역 동반가능");
        assertThat(raw.acmpyPsblCpam()).isEqualTo("전 견종 동반 가능");
        assertThat(raw.acmpyNeedMtr()).isEqualTo("목줄 착용");
        assertThat(raw.etcAcmpyInfo()).startsWith("- 각 가게 개별 정책 문의 필요\n");
        assertThat(raw.relaAcdntRiskMtr()).isNull();
    }

    @Test
    @DisplayName("값이 null 인 칸은 null 로 읽는다")
    void 빈_값() {
        PetTourRaw raw = reader.read(RawFixtures.load("pet-tour/refused"));

        assertThat(raw.acmpyPsblCpam()).isEqualTo("불가");
        assertThat(raw.acmpyTypeCd()).isNull();
        assertThat(raw.acmpyNeedMtr()).isNull();
    }

    @Test
    @DisplayName("petTour 블록이 {} 로 비어 있으면 빈 record 다 — 실물 7건이 이 모양이다")
    void 빈_블록() {
        PetTourRaw raw = reader.read(RawFixtures.load("pet-tour/empty-block"));

        assertThat(raw).isEqualTo(PetTourRaw.withoutBlock());
    }

    @Test
    @DisplayName("petTour 블록 키 자체가 없어도 빈 record 다")
    void 블록_키_없음() {
        Map<String, Object> payload = RawFixtures.load("pet-tour/ikseondong");
        payload.remove("petTour");

        assertThat(reader.read(payload)).isEqualTo(PetTourRaw.withoutBlock());
    }

    @Test
    @DisplayName("블록에 키가 있는데 약속한 키가 빠지면 실패다")
    @SuppressWarnings("unchecked")
    void 키_없음() {
        Map<String, Object> payload = RawFixtures.load("pet-tour/ikseondong");
        ((Map<String, Object>) payload.get("petTour")).remove("acmpyNeedMtr");

        assertThatThrownBy(() -> reader.read(payload))
                .isInstanceOf(PayloadKeyMissingException.class)
                .hasMessageContaining("acmpyNeedMtr");
    }
}
