package com.pawtrail.extract.application.support;

import com.pawtrail.extract.domain.model.PolicyItem;

/**
 * 원문 한 건을 처리한 결과입니다.
 *
 * <pre>
 * SEND      policy 로 보낼 항목이 있음 — 보낸 뒤 처리 완료로
 * SKIPPED   판정 대상이 아님(문화정보원 동물병원) — 보내지 않고 처리 완료로
 * FAILED    이 원문 탓에 뽑지 못함 — 처리 실패로 · 연달아 나면 실행을 멈춤
 * </pre>
 *
 * @param item   SEND 일 때 보낼 항목
 * @param reason SKIPPED · FAILED 일 때 로그에 남길 까닭
 */
public record DocumentOutcome(Kind kind, PolicyItem item, String reason) {

    public enum Kind {
        SEND,
        SKIPPED,
        FAILED
    }

    public static DocumentOutcome send(PolicyItem item) {
        return new DocumentOutcome(Kind.SEND, item, null);
    }

    public static DocumentOutcome skipped(String reason) {
        return new DocumentOutcome(Kind.SKIPPED, null, reason);
    }

    public static DocumentOutcome failed(String reason) {
        return new DocumentOutcome(Kind.FAILED, null, reason);
    }
}
