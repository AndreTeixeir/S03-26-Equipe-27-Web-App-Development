FROM node:22.23.3-alpine3.24 AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# As variáveis VITE_ são lidas no build, não em tempo de execução.
# "/api" é relativo ao próprio endereço: o Caddy repassa essas chamadas ao backend.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM caddy:2.11.7-alpine

# Roda sem root. O binário do Caddy já tem a capability para abrir as portas 80 e 443,
# e o Caddy grava certificados e configuração em /data e /config (volumes nomeados no compose).
RUN addgroup -S -g 10001 web \
    && adduser -S -u 10001 -G web -H -s /sbin/nologin web \
    && chown -R web:web /data /config

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv

USER web
