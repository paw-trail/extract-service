package com.pawtrail.extract.eval;

import java.util.List;
import java.util.Map;

/**
 * 정답 파일의 표본 한 건입니다.
 *
 * <pre>
 * no        정답 파일 안 번호 — 판정할 때 이 번호로 부름
 * id        원문(raw_document) id — 입력은 eval/raw/{id}.json
 * source    PET_TOUR · GOCAMPING · CULTURE_CSV
 * type      뽑힌 까닭 — 일부러 넣은 어려운 유형이거나 무작위
 * fields    조건 20칸의 정답 — 모델이 원문 조각에서 읽어야 할 값 (규칙이 읽는 칸과 합친 값이 아님)
 * evidence  값이 있는 칸마다 그 값을 말한 조각 번호
 * </pre>
 *
 * 목록 칸의 항목은 문자열이거나, 받아 줄 표기들의 목록입니다.
 * 원문이 "수목원 내 카페 실내 좌석" 이면 "카페 실내 좌석" 도 틀리지 않았기 때문입니다.
 */
record EvalSample(
        int no,
        String id,
        String source,
        String type,
        Map<String, Object> fields,
        Map<String, List<Integer>> evidence
) {
}
