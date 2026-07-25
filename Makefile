# Usage: make docker-up ENV=.env.dev
#        make docker-down ENV=.env.dev
#        make docker-logs
ENV ?= .env.dev

DC = docker compose --env-file $(ENV)
DC_DEV = docker compose --env-file $(ENV) -f docker-compose.dev.yml

docker-up:
	$(DC) up -d

docker-down:
	$(DC) down

docker-restart:
	$(DC) restart

docker-logs:
	$(DC) logs -f

docker-build:
	$(DC) up -d --build

dev-up:
	$(DC_DEV) up -d

dev-down:
	$(DC_DEV) down

dev-restart:
	$(DC_DEV) restart

dev-logs:
	$(DC_DEV) logs -f

dev-build:
	$(DC_DEV) up -d --build

.PHONY: docker-up docker-down docker-restart docker-logs docker-build dev-up dev-down dev-restart dev-logs dev-build
