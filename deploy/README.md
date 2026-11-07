# 배포

서버에 Docker를 설치하고 저장소를 받습니다.

```bash
git clone <repository> yechan.app-server && cd yechan.app-server
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
```

`deploy/.env`를 채웁니다. 비밀번호나 토큰은 `openssl rand -base64 32`로 만들면 됩니다.

Cloudflare에서 할 일:

- Zero Trust > Tunnels에서 터널을 만들고 토큰을 `CLOUDFLARE_TUNNEL_TOKEN`에 넣기
- 터널의 Public Hostname에 `api.yechan.app` → `http://api:8080` 연결
- R2에 `yechan-media` 버킷을 만들고 `cdn.yechan.app` 도메인 연결, API 토큰은 `MEDIA_S3_*`에

## 실행

```bash
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d --build
```

업데이트할 때는 `git pull` 후 같은 명령을 다시 실행하면 됩니다. DB 마이그레이션은 시작할 때 자동으로 적용됩니다.

처음 콘텐츠를 넣을 때:

```bash
docker compose -f deploy/compose.yaml --env-file deploy/.env run --rm \
  -v "$PWD/seed/portfolio:/seed:ro" -e APP_SEED_BUNDLE=/seed api
```

로그에 `Imported content bundle`이 보이면 Ctrl+C로 끄면 됩니다.

## 관리자 계정

`AUTH_SETUP_TOKEN`이 있는 상태에서 studio에 들어가 계정을 만듭니다. 다 만들었으면 토큰을 비우고 다시 실행해 둡니다.

로그인 수단을 다 잃어버렸다면 서버에서 지우고 다시 만듭니다. 콘텐츠는 그대로 남습니다.

```bash
docker compose -f deploy/compose.yaml exec postgres psql -U yechan -d yechan \
  -c "delete from passkey; delete from studio_session; update administrator set password_hash = null;"
```

## 백업

매일 새벽 4시에 DB를 백업합니다. 서버에 14일치가 남고, R2를 설정하면 거기에도 올라갑니다.

백업 파일을 암호화하려면 내 컴퓨터에서 키를 만들고, 공개키만 `BACKUP_AGE_RECIPIENT`에 넣습니다. 개인키는 서버에 두지 말고 따로 보관하세요.

```bash
age-keygen -o yechan-backup.key
```

자주 쓰는 명령:

```bash
B="docker compose -f deploy/compose.yaml --env-file deploy/.env exec backup"

$B backup.sh                    # 지금 백업
$B ls -lh /backups              # 백업 목록

# 복원이 되는지 확인 (임시 DB에 넣어 보고 지움)
docker cp yechan-backup.key "$(docker compose -f deploy/compose.yaml ps -q backup)":/tmp/key
$B env AGE_IDENTITY_FILE=/tmp/key restore.sh verify /backups/<파일>

# 실제 복원
docker compose -f deploy/compose.yaml stop api tunnel
$B env AGE_IDENTITY_FILE=/tmp/key RESTORE_CONFIRM=yechan restore.sh apply /backups/<파일>
docker compose -f deploy/compose.yaml start api tunnel
```

한 달에 한 번쯤 복원 확인을 돌려 보는 걸 권장합니다.
