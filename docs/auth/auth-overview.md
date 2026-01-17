### 인증 아키텍처 개요

프로젝트의 인증은 Stateless 아키텍처를 기반으로 JWT Access / Refresh Token 방식을 사용하여 인증을 구현했습니다.

단순 로그인 처리에 그치지 않고, 토큰 탈취, 만료, 로그아웃, 재발급 시나리오까지 운영 환경에서 발생할 수 있는 인증 이슈를 고려한 구조를 목표로 설계했습니다.

#### 인증 구조 요약

- Access Token: 요청 인증용 토큰 (짧은 수명)
- Refresh Token: 토큰 재발급용 (Redis 저장)
- Stateless 기반 인증 처리

#### 설계 핵심 포인트

- 서버 세션을 사용하지 않는 Stateless 인증 구조
- Refresh Token을 Redis에 저장하여 서버 측 토큰 제어 가능
- 로그아웃 시 Access Token 블랙리스트 처리