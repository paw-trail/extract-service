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
 * 온도 0 이라 같은 입력은 같은 답을 받으므로 다시 써도 결과가 같습니다.
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
