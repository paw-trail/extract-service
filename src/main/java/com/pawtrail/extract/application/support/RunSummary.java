package com.pawtrail.extract.application.support;

import java.time.Duration;

/**
 * 실행 한 번의 요약입니다. 끝날 때 로그 한 줄로 찍고, 첫 전량 실행의 검증 숫자가 됩니다.
 *
 * <pre>
 * stop          멈춘 까닭
 * chunks        돈 청크 수
 * fetched       가져온 원문 수
 * sent          policy 로 보낸 항목 수
 * skipped       보내지 않고 처리 완료로 둔 수 (문화정보원 동물병원)
 * failed        처리 실패로 둔 수
 * conflicts     보낸 항목에 담긴 소스 내 충돌 수
 * siblings      한 실행에서 같은 장소 · 같은 소스를 두 번 보낸 수 — 나중 것이 이김
 * statusSkipped 가져간 사이에 내용이 바뀌어 ingest 가 대기로 남긴 수
 * calls         모델을 부른 수
 * reused        같은 입력이라 앞의 답을 다시 쓴 수
 * elapsed       걸린 시간
 * </pre>
 *
 * 수는 policy 로 보내고 ingest 에 되돌려 쓰기까지 끝난 청크만 셉니다.
 * 중간에 멈춘 청크는 원문 상태가 그대로라 다음 실행이 다시 가져가므로 이 요약에 넣지 않습니다.
 * 모델 호출 · 재사용만은 실제로 부른 수라 멈춘 청크의 호출도 들어갑니다.
 */
public record RunSummary(
        Stop stop,
        int chunks,
        int fetched,
        int sent,
        int skipped,
        int failed,
        int conflicts,
        int siblings,
        int statusSkipped,
        int calls,
        int reused,
        Duration elapsed
) {

    /**
     * 멈춘 까닭입니다. 앞의 둘만 정상 끝입니다.
     *
     * <pre>
     * DRAINED            대기 원문이 더 없음
     * LIMIT              요청한 건수만큼 처리함
     * TOO_MANY_FAILURES  문서 탓 실패가 연달아 남 — 처리한 데까지 보내고 되돌려 쓴 뒤 멈춤
     * LLM_UNAVAILABLE    모델을 부를 수 없음 — 그 청크는 상태를 안 바꿈
     * INTERNAL_CALL      ingest · policy 를 부르지 못함 — 그 청크는 상태를 안 바꿈
     * NO_PROGRESS        두 청크 연달아 상태가 하나도 안 바뀜 — 같은 원문을 끝없이 도는 것을 막음
     * BAD_SETTINGS       모델 이름 · 프롬프트 판이 policy 가 받는 길이를 넘음 — 시작하지 않음
     * </pre>
     */
    public enum Stop {
        DRAINED,
        LIMIT,
        TOO_MANY_FAILURES,
        LLM_UNAVAILABLE,
        INTERNAL_CALL,
        NO_PROGRESS,
        BAD_SETTINGS
    }
}
