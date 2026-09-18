package com.pawtrail.extract.infrastructure.provider.convert;

import com.pawtrail.extract.domain.exception.PayloadKeyMissingException;

import java.util.Map;

/**
 * 원문 Map 에서 블록과 값을 꺼냅니다. 읽기 셋이 함께 씁니다.
 *
 * <b>키가 없는 것과 값이 비어 있는 것을 가릅니다.</b>
 * 실물에서 약속한 키는 값이 null 이어도 늘 들어 있습니다 — 문화정보원은 31키가 전부 있고
 * 공사 · 고캠핑도 비어 있는 칸을 null 로 적어 보냅니다. 그러니 키가 없다는 것은
 * 원문의 모양이 바뀌었다는 뜻이고, 그때는 조용히 비우지 않고 실패로 드러냅니다.
 */
final class PayloadMaps {

    private PayloadMaps() {
    }

    /**
     * 블록을 꺼냅니다. 블록이 없거나, 객체가 아니거나, 키가 하나도 없으면 null 입니다.
     *
     * 빈 블록을 "블록 없음" 과 같이 보는 이유 — 공사 원문 7건이 petTour 를 {} 로 싣고 옵니다.
     * 소스가 반려 정보를 아예 안 준 것이라 조건을 말하는 칸이 없는 원문이고,
     * 키가 몇 개 빠진 블록(원문 모양이 바뀐 것)과는 다릅니다.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> block(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) map;
    }

    /**
     * 약속한 키의 값을 문자열로 꺼냅니다. 값이 null 이면 null 입니다.
     *
     * @throws PayloadKeyMissingException 키 자체가 없을 때
     */
    static String text(Map<String, Object> block, String blockName, String key) {
        if (!block.containsKey(key)) {
            throw new PayloadKeyMissingException(blockName, key);
        }
        Object value = block.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
