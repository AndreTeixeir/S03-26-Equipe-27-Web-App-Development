# Infraestrutura (Docker Compose)

Orquestração local/runtime dos serviços do SmartTrafficFlow via Docker Compose.

## Serviços

- `smarttraffic-db`: PostgreSQL com PostGIS (publicado só em `127.0.0.1`).
- `smarttraffic-backend`: API Spring Boot. **Não publica porta**: só é alcançado pelo web, dentro da rede do Compose.
- `smarttraffic-web`: Caddy. Serve o build do frontend (com fallback de SPA para `index.html`), repassa `/api` ao backend e cuida do HTTPS automático. É o único ponto de entrada público (portas 80 e 443).
- `pgadmin`: interface administrativa (opcional via profile `admin`, publicada só em `127.0.0.1`).

### Ordem de inicialização e usuários

- O backend só inicia depois que o banco está `healthy` e o web só inicia depois que o backend está `healthy`. Como o projeto não tem actuator, o healthcheck do backend consulta `GET /api/traffic-records?page=0&size=1`, uma rota leve que passa pelo banco.
- Backend e web rodam **sem root** (usuário `app` e usuário `web`, uid `10001`) e sem capabilities extras; o web mantém apenas `NET_BIND_SERVICE`, para abrir as portas 80 e 443.
- Versões fixadas: Caddy `2.11.7`, Node de build `22.23.3`, pgAdmin `9.18.0`. Para atualizar, troque a tag e teste a stack.

## Subir stack

Copie `.env.example` para `.env` e preencha os valores. As senhas (`SMARTTRAFFIC_DB_PASSWORD` e `SMARTTRAFFIC_PGADMIN_PASSWORD`) **não têm valor padrão**: sem elas o Compose se recusa a subir, com mensagem apontando a variável.

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
```

Os valores de `SMARTTRAFFIC_BACKEND_ENV_FILE` e `SMARTTRAFFIC_DATA_DIR` no exemplo são os do servidor (`/opt/smarttrafficflow/...`). **Fora do servidor, aponte os dois para caminhos locais** antes de subir, senão o Compose falha por arquivo ou pasta inexistente:

```bash
mkdir -p ../.tmp/dados                 # pasta do export.geojson (a .tmp/ da raiz é ignorada pelo git)
cp ../backend/.env.example ../backend/.env   # preencha SPRING_DATASOURCE_PASSWORD (a mesma de SMARTTRAFFIC_DB_PASSWORD)
# no infra/.env:
#   SMARTTRAFFIC_BACKEND_ENV_FILE=../backend/.env
#   SMARTTRAFFIC_DATA_DIR=../.tmp/dados
```

Com pgAdmin:

```bash
docker compose --env-file .env --profile admin up -d --build
```

Sem `SMARTTRAFFIC_DOMAIN` o padrão é `localhost`: o app abre em `https://localhost`, com certificado local gerado pelo Caddy (o navegador avisa que não é confiável; é esperado).

## Subir com domínio (HTTPS automático)

1. Aponte o registro DNS (tipo A) do domínio para o IP público do servidor.
2. Libere as portas **80 e 443** para a internet (security list/regras de entrada da nuvem e firewall do servidor). A 80 é usada pelo Let's Encrypt para validar o domínio e para redirecionar para HTTPS.
3. No `infra/.env`, defina `SMARTTRAFFIC_DOMAIN=seu.dominio.com` e as senhas.
4. Suba a stack com o comando acima. O Caddy obtém o certificado sozinho ao iniciar; acompanhe com `docker compose logs -f smarttraffic-web`.

O CORS do backend usa `https://<SMARTTRAFFIC_DOMAIN>` automaticamente. Só defina `SMARTTRAFFIC_CORS_ALLOWED_ORIGINS` se usar uma porta diferente de 443.

Os certificados ficam nos volumes nomeados `caddy_data` e `caddy_config` e sobrevivem a reinícios e a `docker compose down`. Não use `down -v` em produção: ele apaga também o banco (`postgres_data`) e os certificados.

### Testes locais em outras portas

Para evitar conflito com portas já ocupadas, use as variáveis de bind no `.env`:

```bash
SMARTTRAFFIC_WEB_HTTP_BIND=127.0.0.1:8081
SMARTTRAFFIC_WEB_HTTPS_BIND=127.0.0.1:8443
SMARTTRAFFIC_CORS_ALLOWED_ORIGINS=https://localhost:8443
SMARTTRAFFIC_DB_PORT_BIND=127.0.0.1:55432
```

Nesse modo, acesse direto `https://localhost:8443`. O redirecionamento automático de HTTP para HTTPS só funciona com 80 e 443 publicadas: em portas altas o endereço do redirecionamento perde a porta.

## Checklist do servidor

1. Registro DNS apontando para o IP da instância.
2. Portas 80 e 443 abertas na security list da OCI e no firewall da instância.
3. Fechar as portas 5174 e 8080 para a internet (não são mais usadas; o acesso passa a ser só pelo Caddy).
4. `.env` do servidor com o domínio e as senhas (e `backend.env` com `SPRING_DATASOURCE_PASSWORD`, igual a `SMARTTRAFFIC_DB_PASSWORD`).
   - **Banco já existente:** `POSTGRES_PASSWORD` só vale na primeira criação do volume `postgres_data`. Se o volume do servidor já existe, a senha do `.env` precisa ser **igual à senha atual do banco**, ou deve ser trocada antes (`ALTER USER ... PASSWORD ...` dentro do banco). Se forem diferentes, o backend não autentica, fica `unhealthy` e o web não sobe.
   - `export.geojson`: o diretório e o arquivo precisam ser legíveis pelo uid `10001` do backend (`chmod 755` no diretório e `chmod 644` no arquivo). Se quiser importar as ruas, o `backend.env` precisa de `APP_STREETS_IMPORT_ENABLED=true` e `APP_STREETS_IMPORT_GEOJSON_PATH=/runtime-data/export.geojson`.
   - Não mude `SERVER_PORT` no `backend.env`: o healthcheck e o proxy usam a porta 8080.
5. Deploy: `docker compose --env-file .env up -d --build --remove-orphans` (remove o container antigo do frontend em modo dev).
6. Testar o HTTPS: `https://seu.dominio.com` abre o app e `https://seu.dominio.com/api/traffic-records/summary` responde.
7. Atualizar o link da demo no `README.md` da raiz.

Opcional, depois do deploy: remover o volume antigo do frontend (`docker volume rm <projeto>_frontend_node_modules`).

## Derrubar stack

```bash
docker compose down
```

## Variáveis de ambiente

Use `./.env.example` como base e ajuste o arquivo `./.env`.

Variáveis principais:

- `SMARTTRAFFIC_BACKEND_ENV_FILE`
- `SMARTTRAFFIC_DATA_DIR`
- `SMARTTRAFFIC_DOMAIN`
- `SMARTTRAFFIC_WEB_HTTP_BIND` e `SMARTTRAFFIC_WEB_HTTPS_BIND`
- `SMARTTRAFFIC_CORS_ALLOWED_ORIGINS` (opcional)
- `SMARTTRAFFIC_DB_USER`
- `SMARTTRAFFIC_DB_PASSWORD` (obrigatória)
- `SMARTTRAFFIC_DB_NAME`
- `SMARTTRAFFIC_PGADMIN_EMAIL`
- `SMARTTRAFFIC_PGADMIN_PASSWORD` (obrigatória)

## GeoJSON de ruas

O backend espera ler o arquivo de ruas por mount em `/runtime-data/export.geojson`.  
No Compose, esse mount vem de `SMARTTRAFFIC_DATA_DIR`.

Como o backend roda sem root (uid `10001`), o diretório e o arquivo precisam ser legíveis por outros usuários (por exemplo `chmod 755` no diretório e `chmod 644` no `export.geojson`).

### Como gerar o arquivo sem versionar no Git

Use o Overpass Turbo para gerar o arquivo localmente:

- `https://overpass-turbo.eu/`

Query exemplo:

```overpass
[out:json][timeout:180];
  area["name"="São Paulo"]["admin_level"="8"]->.sp;
  (
    way["highway"](area.sp);
  );
  out ids tags geom;
```

Depois de executar:

1. Exporte em `Export -> GeoJSON`
2. Salve como `export.geojson`
3. Coloque o arquivo no diretório apontado por `SMARTTRAFFIC_DATA_DIR`
