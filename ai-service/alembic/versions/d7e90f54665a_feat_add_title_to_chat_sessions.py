"""feat: add title to chat_sessions

Revision ID: d7e90f54665a
Revises: 20260814_0002
Create Date: 2026-08-16 20:39:37.310808

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'd7e90f54665a'
down_revision = '20260814_0002'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "chat_sessions",
        sa.Column("title", sa.String(100), nullable=True),
        schema="ai_service",
    )


def downgrade() -> None:
    op.drop_column("chat_sessions", "title", schema="ai_service")
