# Microservice workspace

Each directory is an independent Maven/Spring Boot application. A service must be
buildable from its own directory; no service may depend on another service's Java
classes or repository implementation.

```powershell
cd services/account-service
mvn verify
```

To verify all five projects from the repository root:

```powershell
./scripts/ci/verify-services.ps1
```

Ports reserved during the migration:

| Application | Port |
| --- | ---: |
| API Gateway | 8080 |
| Account Service | 8081 |
| Marketplace Service | 8082 |
| Trading Service | 8083 |
| Governance Service | 8084 |

The applications are health-checkable skeletons in work item 1. A business route
is not considered migrated until its owner service, isolated database and contract
tests are complete.
