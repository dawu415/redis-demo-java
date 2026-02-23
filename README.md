# Redis Demo - Spring Boot on TAS

A minimal Spring Boot 3.4 / Java 21 application for validating connectivity to **Redis** on Tanzu Platform for Cloud Foundry, 
and for proving out migration from **Redis Enterprise Brokered Service** to using a **Credhub** service.
For the example steps below, it is assumed you have the CF cli and jq installed on your machine, and that you are logged into
a TPCF foundation via the CF cli.

A dotnet core  version of this application can be found at https://github.com/dawu415/redis-demo-dotnet.

## Tech Stack

- **Java 21** / **Gradle 8.12**
- **Spring Boot 3.4.3** with Spring Data Redis
- **Lettuce** (default async Redis client)
- **java-cfenv** for automatic VCAP_SERVICES parsing on TAS

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/items` | Create a new item |
| `GET` | `/api/items/{id}` | Get item by ID |
| `GET` | `/api/items` | Get all items |
| `GET` | `/api/items/search?name=X` | Search by name |
| `PUT` | `/api/items/{id}` | Update an item |
| `DELETE` | `/api/items/{id}` | Delete an item |
| `GET` | `/api/items/info` | Connection diagnostics (server version, mode, etc.) |

## Build

```bash
./gradlew clean bootJar
```

## Deploy to TAS

```bash
# 1. Create the Redis service instance (adjust plan/service name for your foundation). If adjusting, don't forget to
# update manifest.yml
cf create-service redislabs small-redis redis-enterprise-service

# 2. Push the app
cf push

# 3. Verify
cf apps
cf services
```

## Validate Connectivity

```bash
APP_URL=$(cf app redis-demo | grep routes | awk '{print $2}')

# Check connection info — confirms you're hitting Redis
curl -s https://$APP_URL/api/items/info | jq .

# Create
curl -s -X POST https://$APP_URL/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"test-key","description":"hello from redis!"}' | jq .

# Read
curl -s https://$APP_URL/api/items | jq .

# Get the id
id=$(curl -s https://$APP_URL/api/items | jq -r '.[0].id')
echo "${id}"

# Update (use the id from create response)
curl -s -X PUT https://$APP_URL/api/items/${id} \
  -H "Content-Type: application/json" \
  -d '{"name":"test-key-abc","description":"updated value"}' | jq .

# Delete
curl -s -X DELETE https://$APP_URL/api/items/${id}

# Create a second item
curl -s -X POST https://$APP_URL/api/items \
-H "Content-Type: application/json" \
-d '{"name":"test-key","description":"hello again from redis!"}' | jq .
```

## Migration to Credhub Service

When you're ready to validate against Credhub:

1. For migration, you might receive a new set of credentials to use. For this test, we'll assume the goal is to 
   connect to the same redis instance.  Extract the credentials from the existing Redis Broker Service. 
   The minimum fields needed are `host`,`password` and `port`.

   ```bash 
      cf env 
      # Example only output!
      #
      #     System-Provided:
      #      VCAP_SERVICES: {
      #         "redislabs": [
      #            {
      #               "binding_guid": "0c8bddf1-b73f-4a58-a98d-0179b950a931",
      #               "binding_name": null,
      #               "credentials": {
      #                   "host": "redis-16917.internal.redis-enterprise-01.xx.com",
      #                   "ip_list": [
      #                      "x.x.x.x"
      #                   ],
      #                  "name": "cf-0f523d65-04dd-46f5-90d8-7155e144286e",
      #                  "password": "<SOMETHINGSECRET>",
      #                  "port": 16917,
      #                  "sentinel_addrs": [
      #                     "redis-enterprise-01.xx.com"
      #                  ],
      #                  "sentinel_port": 8001
      #               },
      #               "instance_guid": "0f523d65-04dd-46f5-90d8-7155e144286e",
      #               "instance_name": "redis-enterprise-service",
      #               "label": "redislabs",
      #               "name": "redis-enterprise-service",
      #               "plan": "small-redis",
      #               "provider": null,
      #               "syslog_drain_url": null,
      #               "tags": [
      #                  "redislabs",
      #                  "redis"
      #               ],
      #               "volume_mounts": []
      #            }
      #         ]
      #      }
   
   
   ```

2. Create a **Credhub** service pointing to your Redis Enterprise endpoint: (Take note of the **spring.data.redis.** prefix)
   ```bash
      cf create-service credhub default redis-creds -c '{
       "spring.data.redis.host": "redis-16917.internal.redis-enterprise-01.xx.com",
       "spring.data.redis.password": "<SOMETHINGSECRET>",
       "spring.data.redis.port": 16917,
       "spring.data.redis.ssl.enabled": false
     }'
   ```

3. Redeploy: `cf push -f manifest-credhub.yml`

4. Hit `/api/items/info` to confirm you're now connected to Redis Enterprise (check `redis_version` and `server_name` in the response).
   ```bash
   APP_URL=$(cf app credhub-redis-ent-demo | grep routes | awk '{print $2}')
   
   # Check connection info — confirms you're hitting Redis
   curl -s https://$APP_URL/api/items/info | jq .
   ```

5. Run the same CRUD operations to validate functional parity.

> **Tip:** The `/api/items/info` endpoint is your best friend during migration — it shows you exactly which server you're connected to without needing to SSH into the container.

# Contributions
Thanks to @rabeyta for his time on pairing on this.