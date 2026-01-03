## PreSignedUrl 성능 테스트 

### 테스트 목적

- PreSignedUrl기반의 업로드와 S3의 직접 업로드의 

### 테스트 환경

- 서버 사양 : AWS Lightsail 2vCPU / 2GB RAM
- 네트워크 : 동일 서버 도메인 기준(HTTPS)
- 모니터링 툴 : Prometheus + Grafana
- 측정 기준 : 평균 응답 시간, 처리량, 95% percentile

### 테스트 시나리오

- 업로드 방식 : Presigned URL 방식 / S3 직접 업로드 방식
- 테스트 방식 : 각 방식으로 5회 수동 반복 요청(Postman)
- 업로드 파일: 9.83KB 이미지(PNG)

### S3로 5회 업로드를 한 경우

<img width="1280" height="472" alt="Image" src="https://github.com/user-attachments/assets/0c864b47-2de3-4db7-b8ad-4666221c781e" />

관측 결과
- 평균 처리 시간: 약 80~100ms
- P95 latency: 약 90~110ms
- 처리량: 요청 발생 시 서버 처리량 증가

해석
- 파일 업로드 요청이 애플리케이션 서버를 경유
- 네트워크 I/O 및 S3 전송 비용이 서버 리소스에 직접 반영됨
- 업로드 트래픽 증가 시 서버 부하가 함께 증가하는 구조

### Presigned Url 로 업로드를 한 경우

<img width="1280" height="509" alt="Image" src="https://github.com/user-attachments/assets/963a8965-7e94-454b-b3d8-4d296a11b348" />

관측 결과
- Presigned URL 발급 평균 시간: 약 280~320ms
- P95 latency: 약 300ms 내외
- 서버 처리량: 업로드 트래픽과 무관하게 최소 수준 유지

해석
- 서버는 URL 발급만 담당
- 실제 파일 업로드는 클라이언트 → S3 직접 전송
- 대용량 업로드 시에도 서버 I/O 부담 없음