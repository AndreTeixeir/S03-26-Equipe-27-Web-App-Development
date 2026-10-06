# SmartTrafficFlow

Este é um projeto de hackathon desenvolvido no contexto da comunidade **NoCountry**.  
O objetivo é centralizar análise de tráfego urbano com:

- coleta e cadastro de registros de tráfego,
- visualização de mapa e indicadores,
- geração de insights e exportações,
- simulação de cenários.

## Demonstração & Infraestrutura

A aplicação está publicada em uma instância **Oracle Cloud Infrastructure (OCI)**, na região São Paulo:

- **Demo:** [http://168.138.135.46:5174/](http://168.138.135.46:5174/)

O deploy da stack completa é orquestrado via **Docker Compose**, subindo quatro serviços em containers:

| Serviço | Descrição |
|---|---|
| `smarttraffic-db` | PostgreSQL + PostGIS (banco geoespacial) |
| `smarttraffic-backend` | API Spring Boot |
| `smarttraffic-web` | Servidor web (Caddy): frontend, proxy de `/api` e HTTPS |
| `pgadmin` | Interface de administração do banco |

> Infraestrutura e deploy (provisionamento da instância OCI e configuração do Docker Compose): **André Teixeira**.
> A demo é mantida em ambiente de avaliação e pode ficar indisponível eventualmente.

## Arquitetura

- `backend/`: API Spring Boot (Java 21) com JPA, Flyway e PostgreSQL/PostGIS.
- `frontend/latest-app/`: aplicação React + Vite + TypeScript.
- `infra/`: `docker-compose.yml` para banco, backend, frontend e pgAdmin (perfil opcional).

## Como rodar localmente

### 1. Backend

O backend precisa de um PostgreSQL com PostGIS e **não tem senha padrão**: sem `SPRING_DATASOURCE_PASSWORD` a aplicação se recusa a subir, com uma mensagem apontando a variável que falta.

O Spring Boot **não lê o arquivo `.env` sozinho**. Copie o exemplo, preencha os valores e exporte as variáveis no terminal (ou cadastre-as na configuração de execução da IDE, por exemplo no IntelliJ):

```bash
cd backend
cp .env.example .env
# edite o .env: defina SPRING_DATASOURCE_PASSWORD e, fora do Docker,
# troque o host de SPRING_DATASOURCE_URL para localhost
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 2. Frontend

```bash
cd frontend/latest-app
npm install
npm run dev
```

### 3. Infra com Docker Compose (opcional)

Copie `infra/.env.example` para `infra/.env` e preencha as senhas (`SMARTTRAFFIC_DB_PASSWORD` e `SMARTTRAFFIC_PGADMIN_PASSWORD` são obrigatórias, sem valor padrão):

```bash
cd infra
cp .env.example .env
docker compose --env-file .env up -d --build
```

Para subir também o pgAdmin:

```bash
docker compose --env-file .env --profile admin up -d --build
```

## Variáveis de ambiente

- Backend: use `backend/.env.example` como base.
- Frontend: use `frontend/latest-app/.env.production.example` como base.
- Compose: use `infra/.env.example` como base.

## Fonte do arquivo `export.geojson`

O arquivo de ruas usado na importação do backend **não é versionado** no repositório (o arquivo completo pode ser grande, ex.: ~150 MB).

Ele pode ser gerado pelo Overpass Turbo:

- URL: `https://overpass-turbo.eu/`
- Passos:
1. Acesse a URL.
2. Cole a query abaixo.
3. Ajuste o mapa para a região desejada.
4. Execute (`Run`).
5. Exporte em `Export -> GeoJSON`.
6. Salve como `export.geojson`.

Query exemplo:

```overpass
[out:json][timeout:180];
  area["name"="São Paulo"]["admin_level"="8"]->.sp;
  (
    way["highway"](area.sp);
  );
  out ids tags geom;
```

## Testes

### Backend

```bash
cd backend
./mvnw test
```

### Frontend

```bash
cd frontend/latest-app
npm test
```
