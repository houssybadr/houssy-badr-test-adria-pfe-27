# Plateforme bancaire ENSET / ADRIA

Socle Maven multi-modules : Java 17, Spring Boot 3.5.0 et Spring Cloud 2025.0.0.

| Module | Port | Etat initial |
| --- | --- | --- |
| discovery-service | 8761 | Serveur Eureka |
| config-service | 8888 | Config Server, profil native |
| gateway-service | 8080 | Gateway, routes a ajouter |
| account-service | 8081 | Squelette, couches preparees |
| transaction-service | 8082 | Squelette, import Config Server |
| assistant-service | 8083 | Squelette, propriete de cle preparee |

## Compilation

Depuis la racine : `mvn clean verify`.

## Demarrage local

Dans des terminaux distincts, depuis la racine :

```powershell
mvn -pl discovery-service spring-boot:run
mvn -pl config-service spring-boot:run
mvn -pl account-service spring-boot:run
mvn -pl transaction-service spring-boot:run
mvn -pl gateway-service spring-boot:run
mvn -pl assistant-service spring-boot:run
```

Eureka : http://localhost:8761

Configuration partagee : http://localhost:8888/transaction-service/default

Sante : `http://localhost:<port>/actuator/health`.

Le fichier `.env` est ignore par Git. `.env.example` documente la variable attendue.
Le service assistant importe `.env` comme fichier de proprietes ; la dependance Spring AI sera ajoutee avec l'agent.

## Travail restant

Logique bancaire, persistance, REST, GraphQL, MCP, clients Eureka, OpenFeign,
Resilience4J, routes Gateway, Kafka, Spring AI, client, analyse Python,
Docker, CI/CD et rapport PDF restent a implementer.
