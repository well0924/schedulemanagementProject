# AWS 운영 환경 설정 (보관용)

2026-10에 비용 문제로 서버를 내리면서, 각 서버에 있던 설정 파일을 그대로 옮겨 둔 폴더입니다.
저장소 루트의 `docker-compose*.yml`, `nginx/` 는 로컬 개발용이고, 실제 운영과 부하 테스트는 이 설정으로 했습니다.

## 서버 구성

| 서버 | 내부 IP | 역할 | 파일 |
|---|---|---|---|
| 앱 서버 1, 2 | 10.0.1.145, 10.0.1.130 | Spring Boot 앱, Promtail(로그 수집), node-exporter | `app/` |
| 인프라 서버 | 10.0.1.98 | Nginx(로드밸런서, HTTPS), Kafka 브로커 3개(KRaft), Redis, exporter | `infra/` |
| 모니터링 서버 | 10.0.1.73 | Prometheus, Grafana, Loki, Tempo | `monitoring/` |
| DB | RDS (MySQL 8.0) | 운영 DB | - |

## 파일 설명

- `app/docker-compose.dev.yml`: 앱 서버 2대가 같은 파일을 썼습니다. 비밀값은 서버의 `~/.env` 에서 `env_file` 로 읽습니다. 필요한 변수 목록은 `deploy/.env.example` 에 있습니다.
- `app/promtail-config.yml`: 앱 서버 1의 파일입니다. 앱 서버 2는 `host` 라벨만 `service-1` 로 달랐습니다.
- `infra/docker-compose.infra.yml`: Kafka 브로커 3개(KRaft), Redis, Nginx, certbot, exporter.
- `infra/nginx.conf`: 앱 서버 2대로 요청을 나누는 설정입니다. 부하 테스트 중에 조정한 연결 수, keepalive, 타임아웃 값이 들어 있습니다.
- `monitoring/`: Prometheus 수집 대상, Tempo 설정, 모니터링 스택 compose. Loki는 이미지 기본 설정을 썼습니다.

## 넣지 않은 것

`.env`(비밀값), 인증서, Grafana 데이터(`grafana.db`), DB 덤프는 저장소에 올리지 않고 따로 보관했습니다.
