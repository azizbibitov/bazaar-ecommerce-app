# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Bazaar is a full-stack e-commerce platform with three distinct layers built in parallel, feature by feature (vertical slices - never build an entire layer in isolation):

- **Backend** - Python / FastAPI / PostgreSQL / Redis / Celery / Stripe
- **Shared logic** - Kotlin Multiplatform (KMP), compiled to XCFramework for iOS
- **Native apps** - Swift / SwiftUI: iPhone buyer app (`BazaarApp`) + macOS/iPad admin app (`BazaarAdmin`)

Phase 1 covers: Foundation, Auth, Design System, Catalog, Image uploads, Admin app, Seed/FTS, Error states, Tests, and Integration.

## Repo Structure

```
bazaar/
├── backend/
│   ├── app/
│   │   ├── main.py           # FastAPI entry point
│   │   ├── core/             # config (pydantic-settings), security, DB session
│   │   ├── api/              # route handlers (auth, products, categories, cart, orders, payments)
│   │   ├── models/           # SQLAlchemy 2.0 async ORM models
│   │   ├── schemas/          # Pydantic v2 request/response schemas
│   │   ├── services/         # business logic (no DB queries in route handlers)
│   │   ├── storage/          # S3 / MinIO helpers
│   │   └── tasks/            # Celery background tasks
│   ├── migrations/           # Alembic migrations
│   ├── tests/
│   ├── requirements.txt
│   └── docker-compose.yml
├── shared/                   # Kotlin Multiplatform module
│   └── src/
│       └── commonMain/kotlin/bazaar/
│           ├── models/       # @Serializable data classes
│           ├── network/      # Ktor client, API endpoints
│           ├── repository/   # AuthRepo, ProductRepo, CartRepo, OrderRepo
│           └── validation/   # form validation rules (tested in commonTest)
└── ios/
    ├── BazaarApp/            # iPhone buyer app
    │   ├── Features/         # Auth, Catalog, ProductDetail, Cart, Checkout, Orders
    │   └── Core/             # DI, Router, Theme, Extensions
    └── BazaarAdmin/          # macOS + iPad admin app
        ├── Features/         # Dashboard, Products, Categories, Orders
        └── Core/
```

## Commands

### Backend

```bash
# Start entire local stack (API + PostgreSQL + Redis + MinIO + Celery)
docker compose up

# API with hot reload (outside Docker)
uvicorn app.main:app --reload

# Alembic migrations
alembic revision --autogenerate -m "description"
alembic upgrade head

# Run tests
pytest
pytest tests/test_auth.py          # single file
pytest tests/ -k "test_login"      # single test by name

# Linting (enforced via pre-commit)
black app/ && isort app/ && flake8 app/
```

### KMP

```bash
# Build XCFramework (output goes into shared/build/XCFrameworks/)
./gradlew assembleXCFramework

# Run shared tests (runs on JVM)
./gradlew :shared:test

# Formatting
./gradlew ktlintFormat
```

### iOS / macOS

```bash
# Lint
swiftlint
```

Open `ios/Bazaar.xcworkspace` in Xcode. Both `BazaarApp` (iPhone) and `BazaarAdmin` (macOS/iPad) are targets in the same workspace.

## Architecture Decisions

### Vertical slice development
Each feature is built backend → KMP → Swift before the next feature starts. The only exception is the Foundation slice (steps 1-5), which sets up infrastructure with no iOS UI.

### KMP as the shared truth
KMP holds all domain models, network logic, repositories, and validation. Swift apps **never** define their own model structs or duplicate validation rules. The compiled XCFramework is added to both Xcode targets (embed & sign).

### KMP suspend functions call from Swift
KMP `suspend` functions interop directly with Swift `async/await` - no callbacks needed. Use `Task {}` to bridge sync button taps to async KMP calls.

### Auth flow
- JWT: 15-min access token + 7-day refresh token stored in Redis (key `refresh:{user_id}`)
- iOS stores tokens in Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
- KMP `BazaarAuth` Ktor plugin attaches Bearer token to every request; auto-refreshes on 401 with a Mutex to prevent race conditions
- `AppCoordinator` (owned by `App` struct as `@StateObject`) switches between `AuthFlow` and `MainFlow` based on token presence

### Backend service layer
All business logic lives in `services/`. Route handlers in `api/` only parse requests, call a service, and return responses - no direct DB queries in handlers.

### Navigation (iOS)
`NavigationStack` + typed `Route` enum via `NavigationRouter`. No `NavigationLink(destination:)` - all navigation goes through the coordinator.

### Design system (iOS)
- Colors defined in xcassets with light/dark variants, accessed via `Color` extensions
- `TextStyle` ViewModifier presets (`.title`, `.headline`, `.body`, `.caption`)
- `Spacing` enum constants (`xs:4`, `sm:8`, `md:16`, `lg:24`, `xl:32`) - never hardcode padding values

## Local Services

| Service | Port | Notes |
|---|---|---|
| FastAPI | 8000 | `/docs` for Swagger UI |
| PostgreSQL | 5432 | |
| Redis | 6379 | Cache + Celery broker |
| MinIO | 9000 / 9001 | S3-compatible; UI at `:9001` |

## Key Invariants

- All DB primary keys are UUIDs (`gen_random_uuid()` server default)
- Slugs auto-generated via `python-slugify`, never manually written
- Soft-delete everywhere (`is_active = false`), never hard-delete
- Stock decrement wrapped in a DB transaction with row-level lock
- Stripe webhook handlers must be idempotent (check Stripe event ID)
- Order items store a price snapshot at purchase time - never reference the live product price retroactively
- No force unwraps in Swift (SwiftLint rule enforced)
- No hardcoded API URLs or keys - all via constants/environment variables
- Python type hints required on all function signatures

## Environment Variables

Copy `.env.example` to `.env`. Key variables: `DATABASE_URL`, `REDIS_URL`, `SECRET_KEY`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET`, `AWS_S3_ENDPOINT_URL` (set to `http://localhost:9000` for local MinIO), `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`.

## Testing Strategy

- **Backend**: pytest, FastAPI `TestClient`, separate test DB rolled back after each test. Target: 80% coverage on services layer.
- **KMP**: `commonTest` for all validation and repository logic; `MockEngine` for Ktor to mock API responses.
- **iOS**: `XCTest` for ViewModels; `XCUITest` for critical flows (login, add to cart, checkout).
