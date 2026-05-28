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
