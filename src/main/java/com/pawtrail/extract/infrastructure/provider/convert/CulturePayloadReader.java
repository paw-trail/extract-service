package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.CultureRaw;
import com.pawtrail.extract.domain.provider.PayloadReader;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.pawtrail.extract.infrastructure.provider.convert.PayloadMaps.block;
import static com.pawtrail.extract.infrastructure.provider.convert.PayloadMaps.text;

/**
 * 한국문화정보원 반려동물 동반 가능 문화시설 원문을 읽습니다.
 *
 * 원문은 list 블록 하나에 CSV 한 줄의 31키가 들어 있습니다. 규칙이 쓰는 것은 여덟 키입니다.
 *
 * <b>키를 후보로 나열하지 않습니다.</b>
 * ingest 의 원문 변환기는 "주차 가능여부" 처럼 띄어쓰기가 흔들리는 키를 후보 여럿으로 찾고
 * 다 빗나가면 빈 값으로 둡니다. 장소 칸은 빈칸이 화면에 드러나 그래도 되지만,
 * 조건 칸의 빈 값은 "정보 없음" 으로 판정에 섞여 드러나지 않습니다.
 * 그래서 실물 키 하나만 보고, 없으면 실패로 둡니다.
 */
@Component
public class CulturePayloadReader implements PayloadReader {

    @Override
    public SourceType source() {
        return SourceType.CULTURE_CSV;
    }

    @Override
    public CultureRaw read(Map<String, Object> payload) {
        Map<String, Object> list = block(payload, CultureRaw.BLOCK);
        if (list == null) {
            return CultureRaw.withoutBlock();
        }
        return new CultureRaw(
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_CATEGORY3),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_COMPANION),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_INDOOR),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_OUTDOOR),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_PET_ONLY),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_SIZE),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_FEE),
                text(list, CultureRaw.BLOCK, CultureRaw.KEY_RESTRICTION));
    }
}
