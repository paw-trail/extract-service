package com.pawtrail.extract;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 애플리케이션 컨텍스트가 뜨는지만 확인하는 검사임
// 본문이 비어 있어도 @SpringBootTest 가 앱을 통째로 한 번 띄워보므로
// 빈 배선이 깨졌거나 자동 설정이 안 켜졌으면 여기서 드러남
//
// * 템플릿과 달리 데이터베이스 컨테이너를 띄우지 않음
//   이 서비스는 DB 를 쓰지 않아 DataSource 를 만들 일이 없고
//   데이터 의존성을 걷어내 JPA 자동 설정도 올라오지 않음
//   따라서 설정 서버도 Docker 도 없이 컨텍스트가 뜸
@SpringBootTest
class ExtractApplicationTests {

    @Test
    void contextLoads() {
    }

}
