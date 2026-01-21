### JWT 인증 흐름

<img width="471" height="401" alt="Image" src="https://github.com/user-attachments/assets/ed89816c-2aa5-421f-b2cc-c499c0e962ee" />

본 프로젝트의 인증은 Access Token을 기준으로 JwtFilter에서 요청을 검증하고, 
만료 시 Refresh Token을 통해 재발급하는 구조로 구성되어 있습니다.

#### 토큰 발급 

<img width="644" height="361" alt="Image" src="https://github.com/user-attachments/assets/c09a7334-61ed-4e86-b103-72c8f324fde4" />

- 사용자가 아이디/비밀번호로 로그인 요청 
- AuthenticationManager로 인증 성공 시 TokenProvider에서 Access Token과 Refresh Token을 함께 발급 
- Access Token은 클라이언트에 전달되어 요청 인증에 사용 
- Refresh Token은 Redis에 저장되어 재발급 시 검증에 사용

#### 로그아웃 

<img width="3480" height="1612" alt="Image" src="https://github.com/user-attachments/assets/9bffd326-d46c-4743-a1ef-261a90f396e3" />

- 헤더에 accessToken을 넣고 로그아웃을 요청
- TokenProvider에서 토큰 유효성을 검사
- TokenProvider에서 Access Token의 유효성(만료 포함)을 검증
- RedisService에서 로그아웃한 토큰을 black_list로 저장
- 기존에 저장되어 있는 refreshToken을 삭제
- SecurityContextHolder에 저장된 인증정보를 clear

#### 재발급 흐름

<img width="781" height="261" alt="Image" src="https://github.com/user-attachments/assets/e2fb3a65-9bd6-4f06-b063-a60a4f8166bc" />


- Access Token 만료 시 Refresh Token을 함께 전달
- Refresh Token의 유효성을 검증 
- Redis에 저장된 Refresh Token과 일치 여부 확인 
- 검증 성공 시 새로운 Access / Refresh Token 재발급 
- Redis에 Refresh Token 갱신 저장


#### 보안 고려 사항

- Access Token 수명 제한을 통한 노출 최소화
- Refresh Token 서버 저장으로 탈취 대응
- 로그아웃 시 토큰 무효화 처리
- Stateless 구조 유지로 서버 확장성 확보