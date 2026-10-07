# Frontend (latest-app)

Aplicação React do SmartTrafficFlow para visualização operacional e análise de tráfego.

## Stack

- React 18
- Vite 7 (requer Node 20.19 ou superior, ou Node 22.12 ou superior)
- TypeScript
- Recharts
- Leaflet + React Leaflet
- Vitest 4 + Testing Library

## Executar localmente

```bash
npm install
npm run dev
```

A aplicação sobe na porta `5174` e, sem configuração, chama a API em `http://localhost:8080/api` (backend rodando fora do Docker).

## Build e preview

```bash
npm run build
npm run preview
```

## Testes

```bash
npm test
```

## Configuração de ambiente

Use `./.env.production.example` como base.  
Variável principal:

- `VITE_API_BASE_URL`: endereço da API. É lida **no build**, não em tempo de execução. Sem a variável, o padrão do código é `http://localhost:8080/api` (desenvolvimento). Em produção o valor é `/api`, relativo ao endereço em que o frontend é servido.

## Em produção (Docker)

O `Dockerfile` desta pasta tem dois estágios: o build com Node (`npm ci` e `npm run build`, com `VITE_API_BASE_URL=/api`) e a imagem final do Caddy, que contém só o `dist` e o `Caddyfile`. O Caddy serve os arquivos estáticos (com fallback de SPA para `index.html`), repassa `/api` ao backend e cuida do HTTPS. Veja `infra/README.md` para subir a stack.
