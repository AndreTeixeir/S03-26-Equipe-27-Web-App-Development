# Backend (SmartTrafficFlow)

API REST do SmartTrafficFlow, desenvolvida em Spring Boot para processar e expor dados de tráfego.

## Stack

- Java 21
- Spring Boot 3
- Spring Web / Validation / Data JPA
- Flyway
- PostgreSQL + PostGIS

## Execução local

A senha do banco **não tem valor padrão**. Sem `SPRING_DATASOURCE_PASSWORD` a aplicação se recusa a subir (perfis padrão e `dev`). O Spring Boot não lê o arquivo `.env` sozinho: exporte as variáveis no terminal (`set -a && source .env && set +a`) ou cadastre-as na configuração de execução da IDE.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Execução no perfil padrão:

```bash
./mvnw spring-boot:run
```

## Build e testes

```bash
./mvnw clean package
./mvnw test
```

## Variáveis de ambiente

Use `./.env.example` como referência de configuração.  
Variáveis importantes:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD` (obrigatória, sem valor padrão)
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_STREETS_IMPORT_ENABLED`
- `APP_STREETS_IMPORT_GEOJSON_PATH`
- `APP_RATE_LIMIT_SIMULATIONS_PER_MINUTE` (padrão `5`): requisições por minuto, por IP, em `POST /api/simulations/generate`
- `APP_RATE_LIMIT_TRAFFIC_RECORDS_PER_MINUTE` (padrão `30`): requisições por minuto, por IP, em `POST /api/traffic-records`
- `APP_CLIENT_IP_HEADER` (padrão vazio): cabeçalho com o IP real do cliente, usado pelo limite por IP quando há um proxy na frente da API. O Docker Compose define `X-Forwarded-For`. Fora dele deixe vazio, porque um cliente poderia forjar o cabeçalho para burlar o limite
- `SPRINGDOC_ENABLED` (padrão `false`): publica `/swagger-ui.html` e `/v3/api-docs`; no perfil `dev` eles ficam sempre ligados

## Importação de GeoJSON na inicialização

Quando `APP_STREETS_IMPORT_ENABLED=true`, o backend tenta importar ruas a partir do arquivo definido em `APP_STREETS_IMPORT_GEOJSON_PATH`.

No cenário com Docker Compose, o caminho esperado é:

`/runtime-data/export.geojson`

### Como obter o `export.geojson`

Como o arquivo pode ser grande (ex.: ~150 MB), ele fica fora do Git.

- Acesse `https://overpass-turbo.eu/`
- Execute a query:

```overpass
[out:json][timeout:180];
  area["name"="São Paulo"]["admin_level"="8"]->.sp;
  (
    way["highway"](area.sp);
  );
  out ids tags geom;
```

- Exporte em `Export -> GeoJSON`
- Salve como `export.geojson`
