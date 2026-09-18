package com.pawtrail.extract.application.support;

import com.pawtrail.extract.domain.model.LlmAnswer;
import com.pawtrail.extract.domain.model.Segment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 한 실행 안에서 같은 입력의 모델 답을 다시 씁니다.
 *
 * 공사 원문 996건의 입력이 548가지뿐이고, 문화정보원은 1,618건이 277가지입니다.
 * 같은 문구를 매번 부르면 호출이 두 배 넘게 나갑니다.
 *
 * <b>키는 조각 목록 통째입니다.</b>
 * 조각 · 번호 · 칸이 같아야 같은 입력이고, 그러면 근거 번호도 그대로 맞습니다.
 *
 * <b>OpenAI 추론 모델은 같은 입력에도 답이 조금씩 달라질 수 있습니다.</b>
 * 온도를 정할 수 없어서입니다. 그래서 다시 쓰는 것이 "불러도 같은 답" 이라서가 아니라,
 * 한 실행 안에서 같은 문구를 가진 원문들이 같은 조건을 받게 하려는 것이기도 합니다.
 * 같은 공지를 쓰는 지점 여럿이 실행 안에서 서로 다른 조건을 받는 일을 막습니다.
 *
 * <b>실행 하나에 하나를 만들어 씁니다.</b>
 * 실행을 넘겨 남기지 않습니다 — 프롬프트나 모델을 바꾼 뒤에도 옛 답이 남는 일을 막으려는 것입니다.
 * 실패한 호출은 담지 않습니다.
 */
public class LlmReuse {

    private final Map<List<Segment>, LlmAnswer> answers = new HashMap<>();
    private int calls;
    private int reused;

    public LlmAnswer answerFor(List<Segment> segments, Supplier<LlmAnswer> call) {
        LlmAnswer known = answers.get(segments);
        if (known != null) {
            reused++;
            return known;
        }
        calls++;
        LlmAnswer answer = call.get();
        answers.put(List.copyOf(segments), answer);
        return answer;
    }

    /**
     * 모델을 부른 횟수입니다. 실패한 호출도 셉니다.
     *
     * 재시도는 구현 안에서 일어나므로 여기서는 입력 하나를 보낸 것을 한 번으로 셉니다.
     * 실패한 답은 담지 않으므로, 같은 입력이 다시 오면 다시 부르고 다시 셉니다.
     */
    public int calls() {
        return calls;
    }

    /**
     * 부르지 않고 앞의 답을 다시 쓴 횟수입니다.
     */
    public int reused() {
        return reused;
    }
}
