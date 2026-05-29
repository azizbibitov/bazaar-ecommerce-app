# Bazaar - Build Progress

## Step 01 - Foundation: Project structure, Docker Compose, FastAPI hello world
**Status: Complete**

### What was built
- `backend/` folder created with `app/` package inside
- Python virtual environment at `backend/venv/`
- FastAPI app entry point at `backend/app/main.py` with a single `GET /` endpoint returning `{"status": "ok"}`
- `backend/docker-compose.yml` with PostgreSQL 16 and Redis 7 services
- `backend/requirements.txt` with FastAPI and Uvicorn

### How to verify
```bash
# Start Postgres + Redis
cd backend && docker compose up -d

# Start the API
venv/bin/uvicorn app.main:app --reload

# Check health
curl http://localhost:8000/
# Expected: {"status":"ok"}

# Swagger UI
open http://localhost:8000/docs
```

### Files created
```
backend/
├── app/
│   ├── __init__.py
│   └── main.py
├── venv/                  # gitignored
├── .gitignore
├── .env                   # gitignored
├── .env.example
├── docker-compose.yml
└── requirements.txt
```

---

## Step 02 - SQLAlchemy async + PostgreSQL + Alembic migrations
**Status: Complete**

### What was built
- Added SQLAlchemy 2.0.41 async, asyncpg, Alembic, pydantic-settings to `requirements.txt`
- `backend/app/core/config.py` - reads all env vars from `.env` via pydantic-settings
- `backend/app/core/database.py` - async engine, session factory, `Base` declarative base, `get_db` dependency
- ORM models:
  - `backend/app/models/user.py` - `User` with UUID PK, email, hashed_password, full_name, role (buyer/admin enum), is_active, timestamps
  - `backend/app/models/category.py` - `Category` with UUID PK, name, slug, description, image_url, is_active, timestamps
  - `backend/app/models/product.py` - `Product` and `ProductImage` with UUID PKs, FK relationships, indexes on category_id/slug/is_active
- `migrations/` folder initialized with Alembic, `env.py` configured for async SQLAlchemy
- Initial migration generated and applied; all 4 tables verified in PostgreSQL

### Notes
- SQLAlchemy 2.0.36 was incompatible with Python 3.14 (`str | None` in `Mapped` annotations caused `TypeError`). Upgraded to 2.0.41 which added Python 3.14 support.

### How to verify
```bash
cd backend && docker compose up -d
venv/bin/alembic upgrade head
docker exec backend-postgres-1 psql -U bazaar -d bazaar -c "\dt"
# Expected: users, categories, products, product_images tables
```

### Files created
```
backend/
├── app/
│   ├── core/
│   │   ├── __init__.py
│   │   ├── config.py
│   │   └── database.py
│   └── models/
│       ├── __init__.py
│       ├── user.py
│       ├── category.py
│       └── product.py
├── migrations/
│   ├── versions/
│   │   └── 6a0d54d34818_initial.py
│   ├── env.py
│   └── script.py.mako
├── .env
├── .env.example
└── alembic.ini
```

---

## Step 03 - Auth: JWT + Redis refresh tokens
**Status: Complete**

### What was built
- `app/core/security.py` - bcrypt password hashing (passlib), JWT access tokens (python-jose HS256, 15 min), opaque refresh tokens (`{user_id}:{uuid4}`)
- `app/core/redis.py` - async Redis client (singleton, closed on app shutdown via lifespan)
- `app/schemas/auth.py` - `RegisterRequest`, `LoginRequest`, `TokenResponse`, `RefreshRequest` Pydantic schemas
- `app/services/auth_service.py` - register, login, refresh, logout; all DB/Redis logic lives here, not in route handlers
- `app/api/auth.py` - four endpoints + `get_current_user_id` Bearer dependency
- `app/main.py` - auth router mounted, lifespan closes Redis on shutdown

### Endpoints
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | - | Create buyer account, returns `{id, email, full_name}` |
| POST | `/auth/login` | - | Returns `TokenResponse` (access + refresh tokens) |
| POST | `/auth/refresh` | - | Rotates refresh token, returns new `TokenResponse` |
| POST | `/auth/logout` | Bearer | Deletes refresh token from Redis |

### Token design
- Access token: JWT `sub=user_id`, signed with `SECRET_KEY` (HS256), expires in 15 min
- Refresh token: `{user_id}:{uuid4}`, stored in Redis at `refresh:{user_id}` with 7-day TTL
- Refresh token rotation: each `/refresh` call issues a new refresh token and invalidates the old one

### Notes
- `passlib[bcrypt]==1.7.4` requires `bcrypt==4.0.1` - bcrypt 4.1+ dropped `__about__` which passlib reads at load time
- Added `pydantic[email]` for `EmailStr` support

### How to verify
```bash
cd backend && docker compose up -d
venv/bin/uvicorn app.main:app --reload

# Register
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123","full_name":"Alice"}'

# Login
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret123"}'

# Refresh (use refresh_token from login response)
curl -X POST http://localhost:8000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"<refresh_token>"}'

# Logout (use access_token from login response)
curl -X POST http://localhost:8000/auth/logout \
  -H "Authorization: Bearer <access_token>"
# Expected: 204 No Content
```

### Files created
```
backend/
├── app/
│   ├── api/
│   │   ├── __init__.py
│   │   └── auth.py
│   ├── core/
│   │   ├── redis.py
│   │   └── security.py
│   ├── schemas/
│   │   ├── __init__.py
│   │   └── auth.py
│   ├── services/
│   │   ├── __init__.py
│   │   └── auth_service.py
│   └── main.py              # updated: auth router + lifespan
```

---

## Step 04 - Complete local stack: MinIO, Celery, storage helpers, linting
**Status: Complete**

### What was built
- `docker-compose.yml` - added MinIO (S3-compatible object storage, ports 9000/9001) and Celery worker service
- `Dockerfile.celery` - slim Python image for running the Celery worker in Docker
- `app/core/config.py` - added `aws_access_key_id`, `aws_secret_access_key`, `aws_s3_bucket`, `aws_s3_endpoint_url` settings
- `app/tasks/celery_app.py` - Celery app wired to Redis broker/backend
- `app/tasks/example.py` - placeholder `send_welcome_email` task (stub for future email feature)
- `app/storage/s3.py` - `upload_file`, `get_presigned_url`, `delete_file` via boto3
- `.pre-commit-config.yaml` (repo root) - black, isort, flake8 hooks scoped to `backend/`
- `backend/setup.cfg` - flake8 and isort config (max line 100, black profile)
- Added to `requirements.txt`: `celery`, `boto3`, `python-multipart`, `python-slugify`, `black`, `isort`, `flake8`, `pre-commit`
- Updated `.env.example` with all AWS/MinIO and Stripe variables

### How to verify
```bash
cd backend && docker compose up -d
# MinIO UI: http://localhost:9001 (user: bazaar, password: bazaar123)
# Celery worker starts automatically via docker compose

# Run linters manually
venv/bin/black app/ && venv/bin/isort app/ && venv/bin/flake8 app/
```

### Files created/updated
```
bazaar_proj/
├── .pre-commit-config.yaml       # new
└── backend/
    ├── Dockerfile.celery          # new
    ├── setup.cfg                  # new
    ├── docker-compose.yml         # updated: added minio + celery services
    ├── requirements.txt           # updated: celery, boto3, linting tools
    ├── .env.example               # updated: AWS/MinIO + Stripe vars
    └── app/
        ├── core/
        │   └── config.py          # updated: AWS settings fields
        ├── storage/
        │   ├── __init__.py        # new
        │   └── s3.py              # new
        └── tasks/
            ├── __init__.py        # new
            ├── celery_app.py      # new
            └── example.py        # new
```

---

## Step 05 - pytest setup + auth tests
**Status: Complete**

### What was built
- `pytest.ini` - asyncio auto mode, session-scoped event loop for both fixtures and tests (required for Python 3.14 + asyncpg compatibility)
- `tests/conftest.py` - test infrastructure:
  - `NullPool` engine pointing at `bazaar_test` DB (avoids asyncpg "attached to different loop" errors)
  - Session-scoped `_create_tables` / `_drop_tables` fixtures
  - `_clean_tables` autouse fixture truncates all tables after each test for isolation
  - `db` fixture provides a fresh `AsyncSession` per test
  - `redis_client` fixture uses Redis DB 1 and flushes after each test
  - `client` fixture injects overrides for `get_db` and `get_redis` into the FastAPI app
- `tests/test_auth.py` - 16 tests covering service layer and Web layer:
  - Register: creates user, duplicate raises `ConflictError`
  - Login: returns tokens, wrong password raises `AuthError`, unknown email raises `AuthError`
  - Refresh: rotates tokens, old token invalid after rotation, bad format raises `AuthError`
  - Endpoints: 201 register, 409 duplicate, 200 login, 401 wrong password, 200 refresh, 204 logout, 403 no token

### Notes
- `pytest-asyncio==0.26.0` required (0.24 teardown crashes on Python 3.14)
- `NullPool` on test engine is mandatory - asyncpg connection pool holds connections tied to a specific event loop; NullPool creates fresh connections per query
- `asyncio_default_test_loop_scope = session` in `pytest.ini` - all tests and fixtures share one event loop, preventing "Future attached to a different loop" errors from SQLAlchemy's `asyncio.shield()` in session close

### How to verify
```bash
cd backend && docker compose up -d
venv/bin/pytest tests/test_auth.py -v
# Expected: 16 passed
```

### Files created
```
backend/
├── pytest.ini                 # new
└── tests/
    ├── __init__.py            # new
    ├── conftest.py            # new
    └── test_auth.py           # new
```

---

## Step 06 - KMP sharedLogic auth layer + backend GET /auth/me
**Status: Complete**

### What was built
- Removed wizard placeholder files (Greeting, Platform)
- Added Ktor 3.1.3, kotlinx-coroutines 1.10.2, kotlinx-serialization 1.8.1 to version catalog
- Configured `XCFramework` builder - `assembleSharedLogicXCFramework` task now available
- `BazaarError` sealed class (Network, Auth, Conflict, NotFound, Unknown)
- `AuthTokens` and `UserProfile` `@Serializable` models matching backend schemas
- `TokenStorage` interface + `expect fun createTokenStorage()` factory
- `KeychainTokenStorage` (iOS) - stores tokens in Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
- `InMemoryTokenStorage` (Android stub for Phase 5)
- `BazaarAuthPlugin` - Ktor `ClientPlugin` attaches Bearer token to every outgoing request
- `createBazaarClient` - `HttpClient` factory with `ContentNegotiation` + auth plugin
- `httpEngine()` expect/actual - Darwin on iOS, OkHttp on Android
- `AuthRepository` interface + `AuthRepositoryImpl` with Mutex-protected refresh
- `createAuthRepository()` public factory for Swift consumers
- Backend: fixed `register()` to include `role` in `UserResponse`; added `GET /auth/me` endpoint
- 6 `commonTest` MockEngine tests - all passing on JVM and iOS Simulator targets

### How to verify
```bash
cd shared
./gradlew :sharedLogic:allTests
# Expected: 6 passed (JVM + iOS Simulator)

./gradlew :sharedLogic:assembleSharedLogicXCFramework
# Expected: xcframework written to sharedLogic/build/XCFrameworks/release/SharedLogic.xcframework
```

### Files created
```
shared/sharedLogic/src/
├── commonMain/kotlin/com/bazaar/shared/
│   ├── error/BazaarError.kt
│   ├── models/AuthTokens.kt
│   ├── models/UserProfile.kt
│   ├── network/ApiRequests.kt
│   ├── network/BazaarAuthPlugin.kt
│   ├── network/BazaarHttpClient.kt
│   ├── network/httpEngine.kt
│   ├── repository/AuthRepository.kt
│   ├── repository/AuthRepositoryImpl.kt
│   └── storage/TokenStorage.kt
├── iosMain/kotlin/com/bazaar/shared/
│   ├── network/httpEngine.ios.kt
│   └── storage/KeychainTokenStorage.kt
├── androidMain/kotlin/com/bazaar/shared/
│   ├── network/httpEngine.android.kt
│   └── storage/InMemoryTokenStorage.kt
└── commonTest/kotlin/com/bazaar/shared/
    ├── repository/AuthRepositoryTest.kt
    └── storage/InMemoryTokenStorage.kt
```

---

## Step 07 - iOS buyer app auth screens
**Status: Complete**

### What was built
- Buyer app lives at `shared/iosApp/iosApp.xcodeproj` (KMP wizard project)
- Xcode build phase runs `./gradlew :sharedLogic:embedAndSignAppleFrameworkForXcode` on every build - KMP framework always up to date, no manual XCFramework dragging
- Fixed build phase to export `JAVA_HOME` pointing to Android Studio's bundled JDK
- Design system: `Spacing` enum (xs/sm/md/lg/xl), `BazaarTextStyle` ViewModifier presets
- `BazaarPrimary` and `BazaarError` xcassets color sets with light/dark variants (Xcode auto-generates Swift symbols via `ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS`)
- `AppContainer` - wires `KeychainTokenStorage` + `createAuthRepository()`
- `AppCoordinator` - `@StateObject`, `isAuthenticated` gate, `signOut()`
- `AuthRoute` - typed enum for `NavigationStack`
- `AuthViewModel` - `login()` and `register()` async, typed `BazaarError` catch
- `LoginView` - email/password fields, sign-in button, navigate to register
- `RegisterView` - full name/email/password fields, create account button
- `RootView` - `NavigationStack` switching `AuthFlow` / Main placeholder

### How to verify
Open `shared/iosApp/iosApp.xcodeproj` in Xcode, build and run on simulator. Login and Register screens should appear. Successful auth sets `isAuthenticated = true` and shows the Main placeholder.

### Files created
```
shared/iosApp/iosApp/
├── App/RootView.swift
├── Assets.xcassets/Colors/
│   ├── BazaarPrimary.colorset/Contents.json
│   └── BazaarError.colorset/Contents.json
├── Core/
│   ├── Config/APIConfig.swift
│   ├── DI/AppContainer.swift
│   ├── Navigation/AppCoordinator.swift
│   ├── Navigation/Route.swift
│   └── Theme/
│       ├── Spacing.swift
│       └── TextStyle.swift
└── Features/Auth/
    ├── AuthViewModel.swift
    ├── LoginView.swift
    └── RegisterView.swift
```

### Notes
- `iOSApp.swift` (wizard-generated entry point) needs updating to use `AppCoordinator` - see below
- `ContentView.swift` (wizard-generated) can be deleted once `iOSApp.swift` is wired to `RootView`

### iOSApp.swift - update to
```swift
@main
struct iOSApp: App {
    @StateObject private var coordinator = AppCoordinator(container: AppContainer())

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(coordinator)
        }
    }
}
```
