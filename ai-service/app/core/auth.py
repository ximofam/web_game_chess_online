import base64
import uuid

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import get_settings

_bearer = HTTPBearer()


def get_current_user_id(
        credentials: HTTPAuthorizationCredentials = Depends(_bearer),
) -> uuid.UUID:
    """Decode JWT bearer token and return the subject as a UUID.

    Expects payload: {"sub": "<uuid>", ...}

    ponytail: validates signature with HS256 only.
    Ceiling: algorithm confusion if backend switches to RS256; no iss/aud check.
    Upgrade path: pin algorithms=["HS256"], validate iss + aud claims.
    """
    settings = get_settings()
    if not settings.jwt_secret:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="JWT_SECRET is not configured on the server",
        )

    try:
        secret_bytes = base64.b64decode(settings.jwt_secret)

        payload = jwt.decode(
            credentials.credentials,
            secret_bytes,
            algorithms=["HS256"],
        )
        return uuid.UUID(payload["sub"])
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing token",
        ) from exc


def require_superuser(
    credentials: HTTPAuthorizationCredentials = Depends(_bearer),
) -> uuid.UUID:
    """Decode JWT bearer token and require SUPERUSER role."""
    settings = get_settings()
    if not settings.jwt_secret:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="JWT_SECRET is not configured on the server",
        )

    try:
        secret_bytes = base64.b64decode(settings.jwt_secret)

        payload = jwt.decode(
            credentials.credentials,
            secret_bytes,
            algorithms=["HS256"],
        )
        if payload.get("role") != "SUPERUSER":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Superuser role required",
            )
        return uuid.UUID(payload["sub"])
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing token",
        ) from exc
