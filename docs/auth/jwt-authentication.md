### JWT 인증 흐름

<img width="471" height="401" alt="Image" src="https://github.com/user-attachments/assets/ed89816c-2aa5-421f-b2cc-c499c0e962ee" />

본 프로젝트의 인증은 Access Token을 기준으로 요청을 검증하고, 만료 시 Refresh Token을 통해 재발급하는 구조로 구성되어 있습니다.

#### 토큰 발급 및 재발급 흐름

<img width="644" height="361" alt="Image" src="https://github.com/user-attachments/assets/c09a7334-61ed-4e86-b103-72c8f324fde4" />

<img width="3480" height="1612" alt="Image" src="https://github.com/user-attachments/assets/9bffd326-d46c-4743-a1ef-261a90f396e3" />

<img width="781" height="261" alt="Image" src="https://github.com/user-attachments/assets/e2fb3a65-9bd6-4f06-b063-a60a4f8166bc" />

1. 로그인 성공 시 Access / Refresh Token 발급
2. Refresh Token은 Redis에 저장
3. Access Token 만료 시 Refresh Token 검증 후 재발급
4. 로그아웃 시 Access Token 블랙리스트 처리 및 Refresh Token 삭제

#### OAuth2 로그인 통합

OAuth2 로그인 또한 최종적으로 JWT를 발급하여 일반 로그인과 동일한 인증 흐름으로 처리되도록 설계했습니다.

이를 통해 로그인 방식과 관계없이 동일한 보안 정책과 인증 처리를 적용할 수 있습니다.

#### 보안 고려 사항

- Access Token 수명 제한을 통한 노출 최소화
- Refresh Token 서버 저장으로 탈취 대응
- 로그아웃 시 토큰 무효화 처리
- Stateless 구조 유지로 서버 확장성 확보