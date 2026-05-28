from app.tasks.celery_app import celery


@celery.task
def send_welcome_email(user_id: str, email: str) -> None:
    # placeholder - will be replaced with real email sending
    pass
