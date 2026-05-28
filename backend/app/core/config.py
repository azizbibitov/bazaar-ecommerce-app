from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str
    redis_url: str
    secret_key: str
    access_token_expire_minutes: int = 15
    refresh_token_expire_days: int = 7

    aws_access_key_id: str = "bazaar"
    aws_secret_access_key: str = "bazaar123"
    aws_s3_bucket: str = "bazaar"
    aws_s3_endpoint_url: str = "http://localhost:9000"

    model_config = {"env_file": ".env"}


settings = Settings()
