package com.pawtrail.extract.domain.enums;

/**
 * 원문을 준 곳입니다.
 *
 * ingest 의 raw_document 에 담기는 셋만 둡니다.
 * 행정안전부 동물병원 인허가(MOIS_VET)는 원문을 거치지 않고 place 로 바로 들어가므로
 * 이 서비스가 받을 일이 없습니다.
 *
 * 값 이름은 ingest 의 원문 목록 응답과 policy bulk 요청에 그대로 실리는 이름과 같습니다.
 */
public enum SourceType {

    PET_TOUR,
    GOCAMPING,
    CULTURE_CSV
}
