# Usage: make up ENV=.env.dev
#        make down ENV=.env.dev
#        make logs
ENV ?= .env

DC = docker compose --env-file $(ENV)

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

.PHONY: up down restart logs build
