"""Create the AI service schema and pgvector extension.

Revision ID: 20260814_0001
Revises:
Create Date: 2026-08-14
"""

from alembic import op


revision = "20260814_0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")
    op.execute("CREATE SCHEMA IF NOT EXISTS ai_service")


def downgrade() -> None:
    op.execute("DROP SCHEMA IF EXISTS ai_service CASCADE")
