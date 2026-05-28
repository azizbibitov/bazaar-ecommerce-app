from datetime import timedelta

from redis.asyncio import Redis
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.exceptions import AuthError, ConflictError
from app.core.security import (
    create_access_token,
    create_refresh_token,
    hash_password,
    verify_password,
)
from app.models.user import User, UserRole
from app.schemas.auth import LoginRequest, RegisterRequest, TokenResponse, UserResponse


async def register(db: AsyncSession, req: RegisterRequest) -> UserResponse:
    existing = await db.scalar(select(User).where(User.email == req.email))
    if existing:
        raise ConflictError("Email already registered")

    user = User(
        email=req.email,
        hashed_password=hash_password(req.password),
        full_name=req.full_name,
        role=UserRole.buyer,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return UserResponse(id=user.id, email=user.email, full_name=user.full_name)


async def login(db: AsyncSession, redis: Redis, req: LoginRequest) -> TokenResponse:
    user = await db.scalar(select(User).where(User.email == req.email, User.is_active == True))  # noqa: E712
    if not user or not verify_password(req.password, user.hashed_password):
        raise AuthError("Invalid credentials")

    user_id = str(user.id)
    access_token = create_access_token(user_id)
    refresh_token = create_refresh_token(user_id)
    token_value = refresh_token.split(":", 1)[1]

    ttl = int(timedelta(days=settings.refresh_token_expire_days).total_seconds())
    await redis.setex(f"refresh:{user_id}", ttl, token_value)

    return TokenResponse(access_token=access_token, refresh_token=refresh_token)


async def refresh(redis: Redis, refresh_token: str) -> TokenResponse:
    try:
        user_id, token_value = refresh_token.split(":", 1)
    except ValueError:
        raise AuthError("Invalid refresh token")

    stored = await redis.get(f"refresh:{user_id}")
    if not stored or stored != token_value:
        raise AuthError("Invalid refresh token")

    new_access = create_access_token(user_id)
    new_refresh = create_refresh_token(user_id)
    new_token_value = new_refresh.split(":", 1)[1]

    ttl = int(timedelta(days=settings.refresh_token_expire_days).total_seconds())
    await redis.setex(f"refresh:{user_id}", ttl, new_token_value)

    return TokenResponse(access_token=new_access, refresh_token=new_refresh)


async def logout(redis: Redis, user_id: str) -> None:
    await redis.delete(f"refresh:{user_id}")
