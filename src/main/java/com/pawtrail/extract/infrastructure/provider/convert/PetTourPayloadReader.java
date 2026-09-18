package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.PetTourRaw;
import com.pawtrail.extract.domain.provider.PayloadReader;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.pawtrail.extract.infrastructure.provider.convert.PayloadMaps.block;
import static com.pawtrail.extract.infrastructure.provider.convert.PayloadMaps.text;

/**
 * 한국관광공사 반려동물 동반여행 원문을 읽습니다.
 *
 * 원문은 list · intro · common · petTour 네 블록으로 되어 있고 규칙이 쓰는 것은 petTour 뿐입니다.
 * petTour 블록이 없거나 {} 로 비어 있으면 조건을 말하는 칸이 없는 것이라 빈 record 를 돌려주고,
 * 블록에 키가 있는데 다섯 키 중 하나가 빠졌으면 실패로 둡니다.
 */
@Component
public class PetTourPayloadReader implements PayloadReader {

    @Override
    public SourceType source() {
        return SourceType.PET_TOUR;
    }

    @Override
    public PetTourRaw read(Map<String, Object> payload) {
        Map<String, Object> petTour = block(payload, PetTourRaw.BLOCK);
        if (petTour == null) {
            return PetTourRaw.withoutBlock();
        }
        return new PetTourRaw(
                text(petTour, PetTourRaw.BLOCK, PetTourRaw.KEY_TYPE),
                text(petTour, PetTourRaw.BLOCK, PetTourRaw.KEY_POSSIBLE),
                text(petTour, PetTourRaw.BLOCK, PetTourRaw.KEY_NEED),
                text(petTour, PetTourRaw.BLOCK, PetTourRaw.KEY_ETC),
                text(petTour, PetTourRaw.BLOCK, PetTourRaw.KEY_RISK));
    }
}
