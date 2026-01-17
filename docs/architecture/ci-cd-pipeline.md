### CI/CD 파이프라인
![Image](https://github.com/user-attachments/assets/fcf8cd41-2fbf-44a4-b7ae-1402b5fd85d5)
챠
**구성 요약**
- GitHub Actions 기반 자동 배포 파이프라인
- Jib으로 Docker 이미지 빌드 후 Lightsail 서버로 SSH 배포
- Gradle 캐시(`actions/cache@v3`) 적용으로 빌드 시간 약 1분 단축

**배포 흐름**
1. main 브랜치 merge 시 자동 트리거
2. Gradle 빌드 및 테스트
3. Jib 기반 Docker 이미지 생성
4. Docker Hub 푸시 → Lightsail SSH 접속
5. `docker-compose up -d` 자동 실행

### 기술 선택 이유

- GitHub Actions를 활용해 별도의 CI 서버 운영 부담 없이
  코드 변경부터 배포까지 자동화된 파이프라인을 구성했습니다.
- Jib을 사용하여 Dockerfile 관리 없이
  안정적인 이미지 빌드 환경을 유지했습니다.
- Lightsail 환경 특성상 단순하고 예측 가능한 배포를 우선하여
  SSH + docker-compose 기반 배포 방식을 선택했습니다.