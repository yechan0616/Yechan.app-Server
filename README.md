# yechan.app-server

yechan.app에서 쓰는 CMS 서버입니다.

Kotlin, Spring Boot 4, PostgreSQL, Cloudflare R2

## 실행

JDK 25와 Docker가 필요합니다.

```bash
./gradlew bootRun
```

PostgreSQL과 로컬 S3(S3Mock)가 같이 뜹니다. API 문서는 http://localhost:8080/swagger-ui/index.html 에서 볼 수 있습니다.

Docker로 서비스 올리기:

```bash
docker compose -f deploy/compose.local.yaml --profile app up -d --build
```

테스트:

```bash
./gradlew check
```
