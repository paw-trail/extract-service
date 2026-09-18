package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.model.GoCampingRaw;
import com.pawtrail.extract.domain.provider.PayloadReader;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.pawtrail.extract.infrastructure.provider.convert.PayloadMaps.block;
import static com.pawtrail.extract.infrastructure.provider.convert.PayloadMaps.text;

/**
 * 고캠핑 원문을 읽습니다.
 *
 * 원문은 list 블록 하나에 81키가 들어 있습니다. 규칙이 쓰는 것은 반려동물 출입과 글 칸 열세 개입니다.
 * 글 칸은 GoCampingRaw.TEXT_KEYS 순서대로 담습니다 — 그 순서가 LLM 에 넘길 조각의 순서가 됩니다.
 */
@Component
public class GoCampingPayloadReader implements PayloadReader {

    @Override
    public SourceType source() {
        return SourceType.GOCAMPING;
    }

    @Override
    public GoCampingRaw read(Map<String, Object> payload) {
        Map<String, Object> list = block(payload, GoCampingRaw.BLOCK);
        if (list == null) {
            return GoCampingRaw.withoutBlock();
        }
        Map<String, String> texts = new LinkedHashMap<>();
        for (String key : GoCampingRaw.TEXT_KEYS) {
            texts.put(key, text(list, GoCampingRaw.BLOCK, key));
        }
        return new GoCampingRaw(text(list, GoCampingRaw.BLOCK, GoCampingRaw.KEY_ANIMAL), texts);
    }
}
