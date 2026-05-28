class ConflictError(Exception):
    def __init__(self, detail: str) -> None:
        self.detail = detail


class AuthError(Exception):
    def __init__(self, detail: str) -> None:
        self.detail = detail


class NotFoundError(Exception):
    def __init__(self, detail: str) -> None:
        self.detail = detail
