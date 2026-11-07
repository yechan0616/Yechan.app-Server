# yechan.app-server

yechan.app의 자체 헤드리스 CMS API. `www.yechan.app`(사이트)과 `studio.yechan.app`(관리 화면)이 사용한다.

- Kotlin 2.3 · Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Flyway
- 콘텐츠 구조는 코드가 아니라 **Blueprint**(콘텐츠 타입 정의)로 관리한다. 사이트를 새로 만들어도 서버는 그대로 쓴다.
- studio 로그인은 **패스키**(Touch ID, Face ID)만 지원한다. 비밀번호는 없다.

## 실행

필요: JDK 25, Docker

```bash
./gradlew bootRun     # profile=local, deploy/compose.local.yaml의 PostgreSQL 자동 기동, http://localhost:8080
./gradlew check       # 포맷 + 테스트 + 아키텍처 규칙 검증
```

배포·백업·첫 관리자 등록은 [deploy/README.md](deploy/README.md) 참고.

## 콘텐츠 모델

| 개념 | 설명 |
| --- | --- |
| Blueprint | 콘텐츠 타입. `collection`(여러 entry, slug로 구분) 또는 `singleton`(하나뿐, 사이트 설정 등) |
| Field | `string` `text` `markdown` `number` `integer` `boolean` `url` `email` `date` `datetime` `select` `reference` `list` `object` |
| Entry | Blueprint를 따르는 문서. 초안(draft)을 고치고, 발행(publish)하면 그 시점의 사본이 공개된다 |
| 다국어 | 텍스트 필드에 `localized: true` → `{"en": "...", "ko": "..."}`. 비어 있으면 기본 언어로 대체 |

초안은 형태만 검사하고, 발행할 때 필수 값·참조 대상까지 검사한다.

## API

### 공개 (인증 없음, GET)

| 경로 | 설명 |
| --- | --- |
| `/api/v1/public/locales` | 지원 언어와 기본 언어 |
| `/api/v1/public/collections/{blueprint}?locale=&page=&size=` | 발행된 entry 목록 (position 순) |
| `/api/v1/public/collections/{blueprint}/{slug}?locale=` | 발행된 entry 하나 |
| `/api/v1/public/singletons/{blueprint}?locale=` | singleton entry |

언어는 `locale` → `Accept-Language` → 기본 언어 순으로 고른다. `locale`을 명시한 응답만 CDN에 공유 캐시된다.

### studio (관리자 세션 쿠키 필요)

| 경로 | 설명 |
| --- | --- |
| `GET/POST /api/v1/studio/blueprints`, `GET/PUT/DELETE /{key}` | Blueprint 관리 |
| `GET/POST /api/v1/studio/blueprints/{key}/entries` | entry 목록, 초안 생성 |
| `GET/PUT/DELETE /api/v1/studio/entries/{id}` | entry 조회, 초안 수정(`revision` 필요), 삭제 |
| `PUT/DELETE /api/v1/studio/entries/{id}/publication` | 발행 / 발행 취소 |

### 인증 (`/api/v1/studio/auth`)

| 경로 | 설명 |
| --- | --- |
| `GET /onboarding`, `POST /onboarding/options`, `POST /onboarding` | 첫 관리자 등록 (`X-Setup-Token` 헤더) |
| `POST /sign-in/options`, `POST /sign-in` | 패스키 로그인 (username 없음) |
| `POST /sign-out`, `GET /session` | 로그아웃, 현재 세션 |
| `GET /passkeys`, `POST /passkeys/options`, `POST /passkeys`, `PATCH/DELETE /passkeys/{id}` | 기기(패스키) 관리 |

`*/options`의 `publicKey`는 브라우저에서 `PublicKeyCredential.parseCreationOptionsFromJSON` / `parseRequestOptionsFromJSON`에 그대로 넘기고,
결과는 `credential.toJSON()`을 `{ ceremonyId, credential }`로 보낸다.

### 에러

모든 에러는 RFC 9457 Problem Details(`application/problem+json`)이며 `code`(분기·번역용), `timestamp`, 검증 실패 시 `errors[{field, reason}]`를 포함한다.

## 구조

```text
src/main/kotlin/app/yechan/server/
├── domain/
│   ├── auth/      관리자, 패스키(WebAuthn), 세션
│   └── content/   Blueprint, Entry, 검증·렌더링, 공개 조회
└── global/        config, error, i18n, security, common
deploy/
├── Dockerfile · compose.yaml · .env.example   운영 (api, postgres, tunnel, backup)
├── compose.local.yaml                         로컬 개발 DB
└── backup/                                    백업 이미지와 스크립트
```
