# yechan.app-server

yechan.app의 자체 헤드리스 CMS API. `www.yechan.app`(사이트)과 `studio.yechan.app`(관리 화면)이 사용한다.

- Kotlin 2.3 · Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Flyway
- 콘텐츠 구조는 코드가 아니라 **Blueprint**(콘텐츠 타입 정의)로 관리한다. 사이트를 새로 만들어도 서버는 그대로 쓴다.
- studio 로그인은 **패스키**(Touch ID, Face ID)와 **이메일·비밀번호**를 지원한다.

## 실행

필요: JDK 25, Docker

```bash
./gradlew bootRun     # profile=local, PostgreSQL과 S3 호환 저장소(S3Mock, :9090) 자동 기동, http://localhost:8080
./gradlew check       # 포맷 + 테스트 + 아키텍처 규칙 검증
```

전체 서비스를 Docker로 띄울 때 (api :8080, S3 :9090, 처음 한 번은 데모 콘텐츠도 넣음):

```bash
SEED_BUNDLE=/seed/portfolio docker compose -f deploy/compose.local.yaml --profile app up -d --build
docker compose -f deploy/compose.local.yaml --profile app down     # 중지 (데이터는 볼륨에 남음, 지우려면 -v)
```

로컬에서는 API 문서가 열린다: http://localhost:8080/swagger-ui/index.html (명세 JSON: `/v3/api-docs/public`, `/v3/api-docs/studio`). 운영에서는 꺼져 있다.

### 콘텐츠 번들 가져오기

Blueprint, 이미지, entry를 담은 번들 폴더(`bundle.json` + 이미지 파일)를 서버 검증을 거쳐 한 번에 넣는다. 이미 있는 Blueprint(key)와 entry(slug)는 건너뛰므로 여러 번 실행해도 된다.

```bash
./gradlew bootRun --args='--app.seed.bundle=seed/portfolio'
```

entry 내용 안의 `"@entry:<ref>"`, `"@asset:<ref>"`는 가져오면서 실제 id로 바뀐다. 번들은 사이트 콘텐츠라 저장소에 넣지 않는다(`seed/`는 git 제외).

배포·백업·첫 관리자 등록은 [deploy/README.md](deploy/README.md) 참고.

## 콘텐츠 모델

| 개념 | 설명 |
| --- | --- |
| Blueprint | 콘텐츠 타입. `collection`(여러 entry, slug로 구분) 또는 `singleton`(하나뿐, 사이트 설정 등) |
| Field | `string` `text` `markdown` `number` `integer` `boolean` `url` `email` `date` `datetime` `select` `reference` `asset` `list` `object` |
| Asset | 업로드한 이미지(JPEG, PNG, GIF, WebP). 위치·카메라 정보(EXIF, XMP 등)는 재인코딩 없이 지우고 회전 값만 남긴다. S3 호환 저장소(운영 R2)에 저장하고 CDN 주소로 공개 |
| Entry | Blueprint를 따르는 문서. 초안(draft)을 고치고, 발행(publish)하면 그 시점의 사본이 공개된다 |
| 다국어 | 텍스트 필드에 `localized: true` → `{"en": "...", "ko": "..."}`. 비어 있으면 기본 언어로 대체 |

초안은 형태만 검사하고, 발행할 때 필수 값·참조 대상까지 검사한다.

삭제한 entry는 30일 동안 휴지통에 남고, 발행할 때마다 이력(최근 50개)이 남아 이전 버전을 초안으로 되돌릴 수 있다.

`asset` 필드 값은 `{"asset": "<asset id>", "alt": {"en": "...", "ko": "..."}}`이고, 공개 응답에서는 `{url, width, height, contentType, alt}`로 펼쳐진다.

## API

### 공개 (인증 없음, GET)

| 경로 | 설명 |
| --- | --- |
| `/api/v1/public/locales` | 지원 언어와 기본 언어 |
| `/api/v1/public/collections/{blueprint}?locale=&page=&size=&sort=&filter[field]=` | 발행된 entry 목록 (기본 position 순) |
| `/api/v1/public/collections/{blueprint}/{slug}?locale=` | 발행된 entry 하나 |
| `/api/v1/public/singletons/{blueprint}?locale=` | singleton entry |
| `/api/v1/public/preview/{token}?locale=` | 미리보기 링크로 발행 전 초안 조회 (캐시 금지) |

언어는 `locale` → `Accept-Language` → 기본 언어 순으로 고른다. `locale`을 명시한 응답만 CDN에 공유 캐시된다.

목록 조건은 Blueprint 최상위 필드로 건다.
- `filter[field]=value`: 값이 같은 것만 (boolean, select, string, integer, number, date, reference / 최대 5개)
- `sort=field` 또는 `sort=-field`(내림차순): integer, number, date, datetime, string, select. 같은 값이면 position 순
- 예: `/collections/history?locale=ko&filter[showOnHome]=true&sort=-year`

### studio (관리자 세션 쿠키 필요)

| 경로 | 설명 |
| --- | --- |
| `GET/POST /api/v1/studio/blueprints`, `GET/PUT/DELETE /{key}` | Blueprint 관리 |
| `GET /api/v1/studio/blueprints/{key}/entries?q=&status=` | entry 목록 (검색어, `DRAFT`/`PUBLISHED`/`CHANGED`) |
| `POST /api/v1/studio/blueprints/{key}/entries` | 초안 생성 |
| `GET/PUT /api/v1/studio/entries/{id}` | entry 조회, 초안 수정(`revision` 필요) |
| `DELETE /api/v1/studio/entries/{id}?force=` | 휴지통으로 이동. 다른 entry가 참조하면 409(`force=true`로 강행) |
| `GET /api/v1/studio/entries/{id}/revisions`, `GET /{number}` | 발행 이력 |
| `POST /api/v1/studio/entries/{id}/revisions/{number}/restore` | 이력을 초안으로 되돌리기 (`revision` 필요) |
| `GET /api/v1/studio/trash`, `POST /trash/{id}/restore`, `DELETE /trash/{id}` | 휴지통 목록, 복원, 완전 삭제 (30일 후 자동 삭제) |
| `GET /api/v1/studio/export`, `POST /api/v1/studio/import` (multipart `file`) | 전체 콘텐츠를 zip으로 내보내기 / 가져오기 |
| `PUT/DELETE /api/v1/studio/entries/{id}/publication` | 발행 / 발행 취소 |
| `PUT /api/v1/studio/blueprints/{key}/entries/order` | `{"entries": [id, ...]}` 순서대로 position을 한 번에 저장 (revision 유지) |
| `POST /api/v1/studio/entries/{id}/preview` | 30분짜리 미리보기 토큰 발급 |
| `POST /api/v1/studio/assets` (multipart `file`) | 이미지 업로드. 같은 파일이면 기존 asset 반환(200) |
| `GET /api/v1/studio/assets?unused=`, `GET/DELETE /{id}` | asset 목록(안 쓰는 것만 보기), 조회, 삭제(사용 중이면 409) |
| `POST /api/v1/studio/assets/cleanup` | 하루 넘게 어디에도 쓰이지 않은 asset 일괄 삭제 |

### 인증 (`/api/v1/studio/auth`)

| 경로 | 설명 |
| --- | --- |
| `GET /onboarding`, `POST /onboarding/options`, `POST /onboarding` | 첫 관리자 등록 - 패스키 (`X-Setup-Token` 헤더) |
| `POST /onboarding/password` | 첫 관리자 등록 - 이메일·비밀번호 (`X-Setup-Token` 헤더) |
| `POST /sign-in/options`, `POST /sign-in` | 패스키 로그인 (username 없음) |
| `POST /sign-in/password` | 이메일·비밀번호 로그인 |
| `GET/PATCH /administrator` | 내 정보 조회, 표시 이름·이메일 변경 |
| `PUT /password` | 비밀번호 설정·변경 (이미 있으면 `currentPassword` 필요, 바꾸면 다른 세션 종료) |
| `DELETE /password` | 비밀번호 로그인 끄기 (`currentPassword`, 패스키가 있어야 함) |
| `GET /sessions`, `DELETE /sessions/{id}`, `DELETE /sessions` | 로그인한 기기 목록, 특정 기기 / 다른 모든 기기 로그아웃 |
| `GET /events` | 보안 기록 (로그인 성공·실패, 잠금, 비밀번호·이메일·패스키 변경 / 180일 보관) |
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
│   ├── content/   Blueprint, Entry, 검증·렌더링, 공개 조회
│   └── media/     이미지 업로드, S3 호환 저장소
└── global/        config, error, i18n, security, common
deploy/
├── Dockerfile · compose.yaml · .env.example   운영 (api, postgres, tunnel, backup)
├── compose.local.yaml                         로컬 개발 DB
└── backup/                                    백업 이미지와 스크립트
```
