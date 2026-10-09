### CI/CD 파이프라인
![Image](https://github.com/user-attachments/assets/fcf8cd41-2fbf-44a4-b7ae-1402b5fd85d5)

**구성 요약**
- GitHub Actions 기반 빌드·테스트·배포
- Jib으로 Docker 이미지 빌드 후 Docker Hub(비공개)에 push, 앱 서버 2대(EC2)에 SSH로 배포
- Gradle 캐시(`actions/cache@v3`) 적용
- 비밀값은 이미지에 넣지 않고 서버의 `.env`로만 주입 (변수 목록: `deploy/.env.example`)

**흐름**
1. PR: 테스트 + `jibBuildTar`로 이미지가 만들어지는지만 확인
2. main push: 테스트 → `jib`으로 Docker Hub에 `latest`와 커밋 해시 태그로 push
3. CI 성공 시 `deploy.yml`이 앱 서버에 **한 대씩** SSH 접속해 `docker compose pull && up -d`
4. 첫 서버가 HTTP에 다시 응답한 뒤 두 번째 서버로 넘어가, 배포 중에도 한 대는 요청을 받음

**바뀐 점 (2026-10)**
- 예전 CI는 `jibDockerBuild`라 이미지가 러너 안에서만 만들어지고 사라졌다. 그래서 배포 단계가 Docker Hub의 옛 이미지를 받고 있었고, 실제 반영은 로컬 `./gradlew jib`로 했다.
- 예전 이미지에는 `gradle.properties`의 비밀값(DB, JWT, S3, OpenAI, Google 등)이 환경변수로 들어 있었다. 지금은 `application-*.yml`이 `${ENV}`로만 읽고, 값은 서버 `.env`에 둔다.

**필요한 GitHub Secrets**
- `DOCKER_USERNAME`, `DOCKER_PASSWORD`
- `EC2_APP1_HOST`, `EC2_APP2_HOST`, `EC2_USER`, `EC2_SSH_KEY`
- `EC2_PROXY_HOST` (선택: 점프 서버를 거칠 때만)

### 기술 선택 이유

- GitHub Actions로 별도 CI 서버 없이 코드 변경부터 배포까지 자동화
- Jib으로 Dockerfile 없이 재현 가능한 이미지 빌드
- 서버 2대 규모에서는 SSH + docker compose가 가장 단순하고 예측 가능해, 오케스트레이터 대신 이 방식을 유지
