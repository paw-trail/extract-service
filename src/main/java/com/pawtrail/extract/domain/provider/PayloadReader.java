package com.pawtrail.extract.domain.provider;

import com.pawtrail.extract.domain.enums.SourceType;
import com.pawtrail.extract.domain.exception.PayloadKeyMissingException;
import com.pawtrail.extract.domain.model.SourceRaw;

import java.util.Map;

/**
 * 원문 JSON 을 소스별 record 로 읽습니다.
 *
 * 구현은 infrastructure/provider/convert 에 소스마다 하나씩 있습니다.
 * 원문 형식(키 이름 · 블록 모양)은 거기서만 알고, 규칙은 읽어 낸 record 만 봅니다.
 *
 * 원문은 이미 풀린 Map 으로 받습니다. 문자열을 푸는 일은 원문 목록을 받아 오는 쪽이 맡으므로
 * 이 읽기는 JSON 라이브러리를 모릅니다. ingest 의 원문 변환기와 같은 모양입니다.
 */
public interface PayloadReader {

    SourceType source();

    /**
     * @throws PayloadKeyMissingException 블록은 있는데 약속한 키가 없을 때
     */
    SourceRaw read(Map<String, Object> payload);
}
