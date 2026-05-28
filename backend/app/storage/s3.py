import uuid

import boto3
from botocore.exceptions import ClientError

from app.core.config import settings

_client = None


def get_s3_client():
    global _client
    if _client is None:
        _client = boto3.client(
            "s3",
            endpoint_url=settings.aws_s3_endpoint_url,
            aws_access_key_id=settings.aws_access_key_id,
            aws_secret_access_key=settings.aws_secret_access_key,
        )
    return _client


def upload_file(file_bytes: bytes, content_type: str, prefix: str = "uploads") -> str:
    key = f"{prefix}/{uuid.uuid4()}"
    get_s3_client().put_object(
        Bucket=settings.aws_s3_bucket,
        Key=key,
        Body=file_bytes,
        ContentType=content_type,
    )
    return key


def get_presigned_url(key: str, expires_in: int = 3600) -> str:
    return get_s3_client().generate_presigned_url(
        "get_object",
        Params={"Bucket": settings.aws_s3_bucket, "Key": key},
        ExpiresIn=expires_in,
    )


def delete_file(key: str) -> None:
    try:
        get_s3_client().delete_object(Bucket=settings.aws_s3_bucket, Key=key)
    except ClientError:
        pass
