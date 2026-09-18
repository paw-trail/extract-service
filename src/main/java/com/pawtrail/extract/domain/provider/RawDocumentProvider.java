package com.pawtrail.extract.domain.provider;

import com.pawtrail.extract.domain.model.PendingDocuments;
import com.pawtrail.extract.domain.model.StatusMark;
import com.pawtrail.extract.domain.model.StatusResult;

import java.util.List;

/**
 * ingest 가 가진 원문을 가져오고 처리 결과를 되돌려 씁니다.
 *
 * 구현은 infrastructure/provider/internal 에 있고 /internal 경로로 ingest 를 부릅니다.
 * extract 는 원문 DB 를 직접 읽지 않습니다.
 */
public interface RawDocumentProvider {

    /**
     * 장소에 이어진 처리 대기 원문을 오래된 것부터 가져옵니다. 언제나 첫 쪽입니다.
     *
     * @throws com.pawtrail.extract.domain.exception.InternalCallException ingest 를 부르지 못했을 때
     */
    PendingDocuments findPending(int size);

    /**
     * 처리 결과를 되돌려 씁니다. 한 청크를 한 번에 보냅니다.
     *
     * @throws com.pawtrail.extract.domain.exception.InternalCallException ingest 를 부르지 못했을 때
     */
    StatusResult markStatus(List<StatusMark> done, List<StatusMark> failed);
}
