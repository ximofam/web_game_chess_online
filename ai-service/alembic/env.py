from logging.config import fileConfig

from alembic import context

from app.core.config import get_settings

config = context.config
if config.config_file_name:
    fileConfig(config.config_file_name)

database_url = get_settings().database_url
if not database_url:
    raise RuntimeError("DATABASE_URL is required to run Alembic migrations")
config.set_main_option("sqlalchemy.url", database_url)


def run_migrations_offline() -> None:
    context.configure(
        url=database_url,
        literal_binds=True,
        version_table_schema="ai_service",
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    from sqlalchemy import create_engine, text

    engine = create_engine(database_url)
    with engine.connect() as connection:
        connection.execute(text("CREATE SCHEMA IF NOT EXISTS ai_service"))
        connection.commit()

        context.configure(
            connection=connection,
            version_table_schema="ai_service",
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
