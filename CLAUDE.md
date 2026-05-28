# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Bazaar is a full-stack e-commerce platform built as three independent pieces:

- **Backend** - Python / FastAPI / PostgreSQL / Redis / Celery / Stripe
- **Shared logic** - Kotlin Multiplatform (KMP), compiled to XCFramework for iOS buyer app only
- **Buyer app** - Swift / SwiftUI iPhone app (`BazaarApp`), uses KMP for all business logic
- **Admin app** - Swift / SwiftUI macOS + iPad app (`BazaarAdmin`), standalone - no KMP dependency

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
├── shared/                   # Kotlin Multiplatform - buyer app only
│   └── src/
│       └── commonMain/kotlin/bazaar/
│           ├── models/       # @Serializable data classes
│           ├── network/      # Ktor client, API endpoints
│           ├── repository/   # AuthRepo, ProductRepo, CartRepo, OrderRepo
│           └── validation/   # form validation rules (tested in commonTest)
└── ios/
    ├── BazaarApp/            # iPhone buyer app (uses KMP XCFramework)
    │   ├── Features/         # Auth, Catalog, ProductDetail, Cart, Checkout, Orders
    │   └── Core/             # DI, Router, Theme, Extensions
    └── BazaarAdmin/          # macOS + iPad admin app (pure SwiftUI, no KMP)
        ├── Features/         # Dashboard, Products, Categories, Orders
        ├── Core/             # Router, Theme, Extensions
        └── Network/          # URLSession-based API client (no KMP)
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

### iOS file workflow

When building iOS features, Claude creates Swift files on disk and lists which files to add to which Xcode target. The developer adds them manually in Xcode. Claude never modifies `project.pbxproj` directly.

## Architecture Decisions

### Vertical slice development
Each feature is built backend → KMP → Swift (buyer) before the next feature starts. The only exception is the Foundation slice (steps 1-5), which sets up infrastructure with no iOS UI. Admin features are built backend → Swift (admin) - no KMP step.

### KMP scope: buyer app only
KMP holds all domain models, network logic, repositories, and validation for the **buyer app**. The buyer app never defines its own model structs or duplicates validation rules - everything comes from KMP. The compiled XCFramework is added only to the `BazaarApp` Xcode target.

The admin app is excluded from KMP because it shares zero business logic with the buyer app. Admin features (product/category/order management) are entirely different from buyer features (browsing, cart, checkout). Adding KMP to admin would add Gradle build overhead with no logic reuse.

### Admin app: pure SwiftUI + URLSession
`BazaarAdmin` is a standalone SwiftUI app. It talks to the FastAPI backend directly via `URLSession` + `Codable` Swift structs. No KMP, no Ktor, no shared XCFramework. Admin model structs are defined locally in Swift and may overlap in shape with KMP models but are independent.

### KMP suspend functions call from Swift (buyer app only)
KMP `suspend` functions interop directly with Swift `async/await` - no callbacks needed. Use `Task {}` to bridge sync button taps to async KMP calls.

### Auth flow
- JWT: 15-min access token + 7-day refresh token stored in Redis (key `refresh:{user_id}`)
- Buyer app stores tokens in Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` via KMP
- KMP `BazaarAuth` Ktor plugin attaches Bearer token to every request; auto-refreshes on 401 with a Mutex to prevent race conditions
- Admin app stores tokens in Keychain directly in Swift; handles 401 refresh manually via URLSession interceptor
- `AppCoordinator` (owned by `App` struct as `@StateObject`) switches between `AuthFlow` and `MainFlow` based on token presence - applies to both apps independently

### Backend layered architecture (per "FastAPI: Modern Python Web Development" by Bill Lubanovic)

The backend follows a strict four-component model. Layers communicate only through the APIs immediately adjacent to them - no layer skipping.

```
Web layer      app/api/          HTTP in/out only. Parses requests, calls Service layer, returns responses.
                                 May import schemas and services. Must NOT import Data layer (models/, database.py).

Service layer  app/services/     All business logic. Imports Data layer to read/write data.
                                 Must NOT know about HTTP, request objects, or response formats.

Data layer     app/models/       ORM models (SQLAlchemy), raw DB queries, external service clients (Redis, S3, Stripe).
               app/storage/      Provides the Service layer access to data stores.
               app/tasks/        Must NOT contain business logic.

Model          app/schemas/      Pydantic v2 schemas - shared data definitions used by all layers.
                                 Not a runtime layer; just the shape of data passed between layers.
```

**Enforcement rules:**
- Route handlers in `api/` call exactly one service function per endpoint, then return
- No `db.execute(...)` or SQLAlchemy queries in `api/`
- No `Request`/`Response` FastAPI objects in `services/`
- Services receive plain Python values or Pydantic schemas - never FastAPI dependencies
- The Data layer (models, storage, tasks) is never imported directly by `api/`

### Navigation (iOS)
`NavigationStack` + typed `Route` enum via `NavigationRouter`. No `NavigationLink(destination:)` - all navigation goes through the coordinator. Applies to both `BazaarApp` and `BazaarAdmin`.

### Design system (iOS)
- Colors defined in xcassets with light/dark variants, accessed via `Color` extensions
- `TextStyle` ViewModifier presets (`.title`, `.headline`, `.body`, `.caption`)
- `Spacing` enum constants (`xs:4`, `sm:8`, `md:16`, `lg:24`, `xl:32`) - never hardcode padding values
- Both apps share the same design system conventions; `BazaarAdmin` defines its own xcassets independently (no shared asset catalog)

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
- **Buyer app**: `XCTest` for ViewModels; `XCUITest` for critical flows (login, add to cart, checkout).
- **Admin app**: `XCTest` for ViewModels only; no UI automation tests (internal tool, lower risk).
