# 운영 가이드

Linux 서버 한 대에서 Docker Compose로 돌리고, 외부 공개는 Cloudflare Tunnel로만 한다. 서버 방화벽에서 인바운드 포트를 열 필요가 없다 (SSH 제외).

```text
브라우저 ──HTTPS──▶ Cloudflare ──Tunnel──▶ tunnel(cloudflared) ──▶ api:8080 ──▶ postgres
                                                               backup ──▶ ./backups ──▶ R2 (선택)
```

## 1. 서버 준비

```bash
# Docker Engine + Compose 플러그인 (Ubuntu 기준: https://docs.docker.com/engine/install/ubuntu/)
sudo usermod -aG docker "$USER"   # 다시 로그인

git clone <repository> yechan.app-server && cd yechan.app-server
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
```

`deploy/.env`에서 `DB_PASSWORD`, `AUTH_SETUP_TOKEN`을 `openssl rand -base64 32`로 채운다.

## 2. Cloudflare Tunnel

1. Cloudflare 대시보드 → Zero Trust → Networks → Tunnels → **Create a tunnel** (Cloudflared)
2. 발급된 토큰을 `CLOUDFLARE_TUNNEL_TOKEN`에 넣는다. (설치 명령은 실행하지 않는다. compose의 `tunnel` 서비스가 대신한다)
3. **Public Hostname** 추가: `api.yechan.app` → Service `HTTP` / `api:8080`
4. 권장 설정
   - SSL/TLS 모드 Full, Always Use HTTPS
   - WAF → Rate limiting: `/api/v1/studio/auth/*` 경로에 IP당 분당 20회 정도
   - Cache Rules: `/api/v1/public/*`는 Origin Cache-Control 존중 (`?locale=` 응답만 공유 캐시된다)

## 3. 실행

```bash
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build
docker compose -f deploy/compose.yaml ps        # 모두 healthy 인지 확인
curl https://api.yechan.app/actuator/health     # {"status":"UP",...}
```

업데이트: `git pull && docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build`
DB 마이그레이션(Flyway)은 api가 시작할 때 자동 적용된다.

## 4. 첫 관리자 등록 (패스키)

1. `AUTH_SETUP_TOKEN`이 설정된 상태에서 studio의 온보딩 화면에 토큰을 입력하고 Touch ID로 패스키를 만든다.
   (`GET /api/v1/studio/auth/onboarding` → `available: true`일 때만 가능)
2. 등록이 끝나면 `AUTH_SETUP_TOKEN=`을 비우고 `up -d`로 재시작한다. 패스키가 하나라도 있으면 온보딩은 자동으로 닫히지만, 토큰도 지워 두는 것이 안전하다.
3. 다른 기기(iPhone 등)는 로그인한 상태에서 studio의 패스키 관리 화면에서 추가한다. iCloud 키체인으로 동기화된 패스키는 같은 Apple 계정의 기기에서 바로 쓸 수 있다.

**패스키를 모두 잃어버린 경우**: 서버에서 패스키만 지우고 같은 username으로 다시 온보딩한다. 콘텐츠와 관리자 정보는 그대로 남는다.

```bash
docker compose -f deploy/compose.yaml exec postgres psql -U yechan -d yechan -c "delete from passkey; delete from studio_session;"
# AUTH_SETUP_TOKEN 다시 설정 → up -d → 온보딩
```

## 5. 백업

`backup` 서비스가 매일 04:00 (KST)에 자동으로 수행한다.

| 단계 | 내용 |
| --- | --- |
| 덤프 | `pg_dump` custom 형식, 압축. `pg_restore --list`로 읽을 수 있는지 바로 확인 |
| 암호화 (권장) | `BACKUP_AGE_RECIPIENT`(age 공개키)가 있으면 `.dump.age`로 암호화. 서버에는 공개키만 둔다 |
| 로컬 보관 | `deploy/backups/`, 기본 14일 |
| 원격 보관 (권장) | `BACKUP_REMOTE`가 있으면 Cloudflare R2로 업로드. daily 35일, 매주 일요일분은 weekly에 1년 |
| 감시 | 성공하면 `BACKUP_HEALTHCHECK_URL` 호출, 실패하면 `/fail` 호출. 컨테이너 healthcheck는 26시간 안에 성공 기록이 없으면 unhealthy |

### 권장 구성 (한 번만)

```bash
# 1) 암호화 키: 로컬 PC에서 만든다. 개인키(AGE-SECRET-KEY-...)는 비밀번호 관리자에 보관하고 서버에 두지 않는다.
age-keygen -o yechan-backup.key        # 출력된 공개키(age1...)를 BACKUP_AGE_RECIPIENT에

# 2) R2: 버킷(yechan-backups) 생성 → R2 API 토큰(해당 버킷 Object Read & Write) 발급
#    R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_ENDPOINT, BACKUP_REMOTE=r2:yechan-backups/postgres

# 3) 알림: healthchecks.io 같은 서비스에 하루 주기 체크를 만들고 URL을 BACKUP_HEALTHCHECK_URL에
```

### 수동 백업 / 복원

```bash
B="docker compose -f deploy/compose.yaml --env-file deploy/.env exec backup"

$B backup.sh                                                   # 지금 바로 백업
$B ls -lh /backups                                             # 로컬 백업 목록

# 복원 훈련 (매달 권장): 임시 DB에 복원해 보고 지운다. 운영 DB는 건드리지 않는다.
docker cp yechan-backup.key "$(docker compose -f deploy/compose.yaml ps -q backup)":/tmp/key
$B env AGE_IDENTITY_FILE=/tmp/key restore.sh verify /backups/<파일>.dump.age
$B env AGE_IDENTITY_FILE=/tmp/key restore.sh verify r2:yechan-backups/postgres/daily/<파일>.dump.age

# 실제 복원: api를 멈추고 덮어쓴 뒤 다시 켠다.
docker compose -f deploy/compose.yaml stop api tunnel
$B env AGE_IDENTITY_FILE=/tmp/key RESTORE_CONFIRM=yechan restore.sh apply /backups/<파일>.dump.age
docker compose -f deploy/compose.yaml start api tunnel
$B rm /tmp/key
```

## 6. 점검 목록

- [ ] `docker compose ps`에서 모든 서비스가 healthy
- [ ] `deploy/.env` 권한이 600이고 `AUTH_SETUP_TOKEN`이 비어 있음
- [ ] 백업 알림이 매일 오고, 한 달에 한 번 `restore.sh verify`를 돌려 봤음
- [ ] age 개인키가 서버 밖 두 곳 이상에 보관되어 있음
