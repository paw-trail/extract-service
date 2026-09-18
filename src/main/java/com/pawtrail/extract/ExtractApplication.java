package com.pawtrail.extract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 서비스 진입점입니다.
 *
 * 공통 모듈(com.pawtrail.common)은 자동 설정으로 등록되므로 컴포넌트 스캔 대상에 넣지 않습니다.
 * 넣으면 같은 설정이 자동 설정과 스캔 양쪽에 잡혀 두 번 등록되고,
 * 조건 평가 순서가 깨져 의도와 다른 Bean이 올라갈 수 있습니다.
 * 공통 모듈은 의존성만 추가하면 조건에 맞는 Bean이 알아서 올라옵니다.
 *
 * 이 서비스는 DB 를 쓰지 않으므로 템플릿에 있던 엔티티 스캔과 저장소 활성화 애노테이션 두 줄,
 * 그리고 그 import 를 지웠습니다.
 * build.gradle 에서 데이터 의존성도 함께 걷어냈으므로
 * 공통 모듈의 JPA 자동 설정은 클래스가 없어 아예 올라오지 않습니다.
 *
 * 스캔 범위를 따로 지정하지 않은 이유는 기본값이 이 클래스가 속한 패키지이기 때문입니다.
 */
@SpringBootApplication
public class ExtractApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtractApplication.class, args);
    }
}
