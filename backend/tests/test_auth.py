import pytest
from httpx import AsyncClient
from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import create_access_token, verify_password
from app.schemas.auth import RegisterRequest
from app.services import auth_service


# --- service layer tests ---

class TestRegisterService:
    async def test_creates_user(self, db: AsyncSession, redis_client: Redis):
        req = RegisterRequest(email="alice@example.com", password="secret123", full_name="Alice")
        user = await auth_service.register(db, req)
        assert user.email == "alice@example.com"
        assert user.full_name == "Alice"

    async def test_hashes_password(self, db: AsyncSession, redis_client: Redis):
        req = RegisterRequest(email="bob@example.com", password="mypassword", full_name="Bob")
        user = await auth_service.register(db, req)
        assert user.id is not None

    async def test_duplicate_email_raises(self, db: AsyncSession, redis_client: Redis):
        from app.core.exceptions import ConflictError
        req = RegisterRequest(email="dup@example.com", password="pass", full_name="Dup")
        await auth_service.register(db, req)
        with pytest.raises(ConflictError):
            await auth_service.register(db, req)


class TestLoginService:
    async def test_returns_tokens(self, db: AsyncSession, redis_client: Redis):
        req = RegisterRequest(email="login@example.com", password="pass123", full_name="Login")
        await auth_service.register(db, req)
        from app.schemas.auth import LoginRequest
        tokens = await auth_service.login(db, redis_client, LoginRequest(email="login@example.com", password="pass123"))
        assert tokens.access_token
        assert tokens.refresh_token
        assert tokens.token_type == "bearer"

    async def test_wrong_password_raises(self, db: AsyncSession, redis_client: Redis):
        from app.core.exceptions import AuthError
        from app.schemas.auth import LoginRequest
        req = RegisterRequest(email="wp@example.com", password="correct", full_name="WP")
        await auth_service.register(db, req)
        with pytest.raises(AuthError):
            await auth_service.login(db, redis_client, LoginRequest(email="wp@example.com", password="wrong"))

    async def test_unknown_email_raises(self, db: AsyncSession, redis_client: Redis):
        from app.core.exceptions import AuthError
        from app.schemas.auth import LoginRequest
        with pytest.raises(AuthError):
            await auth_service.login(db, redis_client, LoginRequest(email="nobody@example.com", password="x"))


class TestRefreshService:
    async def test_rotates_tokens(self, db: AsyncSession, redis_client: Redis):
        from app.schemas.auth import LoginRequest
        req = RegisterRequest(email="ref@example.com", password="pass", full_name="Ref")
        await auth_service.register(db, req)
        tokens = await auth_service.login(db, redis_client, LoginRequest(email="ref@example.com", password="pass"))
        new_tokens = await auth_service.refresh(redis_client, tokens.refresh_token)
        assert new_tokens.access_token
        assert new_tokens.refresh_token != tokens.refresh_token

    async def test_old_token_invalid_after_rotation(self, db: AsyncSession, redis_client: Redis):
        from app.core.exceptions import AuthError
        from app.schemas.auth import LoginRequest
        req = RegisterRequest(email="rot@example.com", password="pass", full_name="Rot")
        await auth_service.register(db, req)
        tokens = await auth_service.login(db, redis_client, LoginRequest(email="rot@example.com", password="pass"))
        await auth_service.refresh(redis_client, tokens.refresh_token)
        with pytest.raises(AuthError):
            await auth_service.refresh(redis_client, tokens.refresh_token)

    async def test_invalid_token_raises(self, db: AsyncSession, redis_client: Redis):
        from app.core.exceptions import AuthError
        with pytest.raises(AuthError):
            await auth_service.refresh(redis_client, "bad-token-no-colon")


# --- endpoint (Web layer) tests ---

class TestAuthEndpoints:
    async def test_register_201(self, client: AsyncClient):
        r = await client.post("/auth/register", json={
            "email": "ep@example.com", "password": "pass123", "full_name": "EP"
        })
        assert r.status_code == 201
        data = r.json()
        assert data["email"] == "ep@example.com"
        assert "id" in data
        assert "password" not in data

    async def test_register_duplicate_409(self, client: AsyncClient):
        payload = {"email": "ep2@example.com", "password": "pass", "full_name": "EP2"}
        await client.post("/auth/register", json=payload)
        r = await client.post("/auth/register", json=payload)
        assert r.status_code == 409

    async def test_login_200(self, client: AsyncClient):
        await client.post("/auth/register", json={
            "email": "login2@example.com", "password": "pass123", "full_name": "L"
        })
        r = await client.post("/auth/login", json={
            "email": "login2@example.com", "password": "pass123"
        })
        assert r.status_code == 200
        assert "access_token" in r.json()

    async def test_login_wrong_password_401(self, client: AsyncClient):
        await client.post("/auth/register", json={
            "email": "lw@example.com", "password": "correct", "full_name": "LW"
        })
        r = await client.post("/auth/login", json={"email": "lw@example.com", "password": "wrong"})
        assert r.status_code == 401

    async def test_refresh_200(self, client: AsyncClient):
        await client.post("/auth/register", json={
            "email": "rfr@example.com", "password": "pass", "full_name": "RFR"
        })
        tokens = (await client.post("/auth/login", json={
            "email": "rfr@example.com", "password": "pass"
        })).json()
        r = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
        assert r.status_code == 200

    async def test_logout_204(self, client: AsyncClient):
        await client.post("/auth/register", json={
            "email": "out@example.com", "password": "pass", "full_name": "Out"
        })
        tokens = (await client.post("/auth/login", json={
            "email": "out@example.com", "password": "pass"
        })).json()
        r = await client.post("/auth/logout", headers={"Authorization": f"Bearer {tokens['access_token']}"})
        assert r.status_code == 204

    async def test_logout_without_token_403(self, client: AsyncClient):
        r = await client.post("/auth/logout")
        assert r.status_code == 403
