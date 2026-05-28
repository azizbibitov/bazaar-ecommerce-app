# Bazaar

A full-stack e-commerce platform built with FastAPI, Kotlin Multiplatform, and SwiftUI.

## Stack

- **Backend** - Python / FastAPI / PostgreSQL / Redis / Celery / Stripe
- **Shared logic** - Kotlin Multiplatform (KMP), compiled to XCFramework for iOS
- **Native apps** - Swift / SwiftUI: iPhone buyer app (`BazaarApp`) + macOS/iPad admin app (`BazaarAdmin`)

## Repo Structure

```
bazaar/
├── backend/
│   ├── app/
│   │   ├── main.py           # FastAPI entry point
│   │   ├── core/             # config, security, DB session, Redis, exceptions
│   │   ├── api/              # route handlers (auth, products, categories, cart, orders, payments)
│   │   ├── models/           # SQLAlchemy 2.0 async ORM models
│   │   ├── schemas/          # Pydantic v2 request/response schemas
│   │   ├── services/         # business logic
│   │   ├── storage/          # S3 / MinIO helpers
│   │   └── tasks/            # Celery background tasks
│   ├── migrations/           # Alembic migrations
│   ├── tests/
│   ├── requirements.txt
│   └── docker-compose.yml
├── shared/                   # Kotlin Multiplatform module
│   └── src/
│       └── commonMain/kotlin/bazaar/
│           ├── models/
│           ├── network/
│           ├── repository/
│           └── validation/
└── ios/
    ├── BazaarApp/            # iPhone buyer app
    └── BazaarAdmin/          # macOS + iPad admin app
```

## Getting Started

### Backend

```bash
cd backend

# Start PostgreSQL, Redis, and MinIO
docker compose up -d

# Create virtual environment and install dependencies
python -m venv venv
venv/bin/pip install -r requirements.txt

# Copy env file and fill in values
cp .env.example .env

# Run migrations
venv/bin/alembic upgrade head

# Start the API (hot reload)
venv/bin/uvicorn app.main:app --reload
```

API docs available at `http://localhost:8000/docs`.

### iOS / macOS

Open `ios/Bazaar.xcworkspace` in Xcode. Targets: `BazaarApp` (iPhone) and `BazaarAdmin` (macOS/iPad).

### KMP

```bash
# Build XCFramework
./gradlew assembleXCFramework

# Run shared tests
./gradlew :shared:test
```

## Local Services

| Service    | Port       | Notes                  |
|------------|------------|------------------------|
| FastAPI    | 8000       | `/docs` for Swagger UI |
| PostgreSQL | 5432       |                        |
| Redis      | 6379       | Cache + Celery broker  |
| MinIO      | 9000 / 9001 | S3-compatible; UI at `:9001` |

## Environment Variables

Copy `.env.example` to `.env`. Key variables:

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL async URL (`postgresql+asyncpg://...`) |
| `REDIS_URL` | Redis URL |
| `SECRET_KEY` | JWT signing secret |
| `AWS_ACCESS_KEY_ID` | MinIO / S3 key |
| `AWS_SECRET_ACCESS_KEY` | MinIO / S3 secret |
| `AWS_S3_BUCKET` | Bucket name |
| `AWS_S3_ENDPOINT_URL` | Set to `http://localhost:9000` for local MinIO |
| `STRIPE_SECRET_KEY` | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret |
