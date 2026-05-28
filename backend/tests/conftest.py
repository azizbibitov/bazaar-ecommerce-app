from typing import AsyncGenerator

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.core.database import Base, get_db
from app.core.redis import get_redis
from app.main import app

TEST_DATABASE_URL = "postgresql+asyncpg://bazaar:bazaar@localhost/bazaar_test"

_test_engine = create_async_engine(TEST_DATABASE_URL, poolclass=NullPool)
_TestSessionLocal = async_sessionmaker(_test_engine, expire_on_commit=False)


@pytest_asyncio.fixture(scope="session", autouse=True)
async def _create_tables():
    async with _test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with _test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture(autouse=True)
async def _clean_tables():
    yield
    async with _test_engine.begin() as conn:
        for table in reversed(Base.metadata.sorted_tables):
            await conn.execute(table.delete())


@pytest_asyncio.fixture
async def db() -> AsyncGenerator[AsyncSession, None]:
    async with _TestSessionLocal() as session:
        yield session


@pytest_asyncio.fixture
async def redis_client() -> AsyncGenerator[Redis, None]:
    client = Redis.from_url("redis://localhost:6379/1", decode_responses=True)
    yield client
    await client.flushdb()
    await client.aclose()


@pytest_asyncio.fixture
async def client(db: AsyncSession, redis_client: Redis) -> AsyncGenerator[AsyncClient, None]:
    app.dependency_overrides[get_db] = lambda: db
    app.dependency_overrides[get_redis] = lambda: redis_client
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()
