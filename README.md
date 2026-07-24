# NextEdge AI

NextEdge AI is a private, multi-tenant data automation platform. It combines a Java/Spring backend, a React/TypeScript operator console, a Node.js reverse proxy, MongoDB, Redis, and compatibility services for workloads that are still being migrated from their original cloud APIs.

This repository is private. Do not publish source, screenshots, credentials, architecture details, customer data, or deployment output.

## Current phase-one scope

- Product name: **NextEdge AI**
- Tenant identifier: **NextEdge ID** (`nextEdgeId`)
- Authentication: password-based JWT sessions
- AI provider: Amazon Bedrock through the EC2 IAM role
- Approved connectors: File/CSV, Amazon S3, PostgreSQL, MySQL, and MongoDB
- Deployment region: AWS `ap-south-1`
- Demo compute: one isolated Ubuntu `m6i.2xlarge` EC2 instance managed through SSM

The live demo is a single-node environment, not a highly available production topology.

## Repository layout

| Path | Purpose |
| --- | --- |
| `backend-master/arcade` | Main browser-facing REST API |
| `backend-master/core` | Domain models, tenant routing, services, migrations, security, and AI provider abstraction |
| `backend-master/connector` | Connector framework and connector implementations |
| `backend-master/dbm` | System and per-tenant MongoDB migrations |
| `backend-master/viper` | Sync processing workers |
| `backend-master/karibu` | Additional API surface |
| `spectrum-master` | React/TypeScript application |
| `spectrum-master/proxy` | Node.js reverse proxy and static frontend runtime |
| `infrastructure/aws` | CloudFormation, deployment scripts, operations, monitoring, backups, and cost controls |
| `demo` | Private demo support material that contains no credentials |
| `design-system` | NextEdge AI visual system and UI guidance |

## Runtime architecture

The demo uses Docker Compose on the dedicated EC2 instance. Only the web proxy is exposed on port 80; databases and backend services remain on a private Docker network. Administration uses AWS Systems Manager Session Manager—SSH is not open.

MongoDB contains one system database and a separate database for each NextEdge ID. Startup runs system migrations first, then customer migrations for every configured tenant. The live verification suite logs in as two independent users and tests that neither tenant can read the other tenant's resources.

The current compatibility services emulate the subset of Google Pub/Sub, object storage, and analytical APIs required by legacy modules. They are private implementation details and can be replaced incrementally without changing the public NextEdge AI interface.

## Build

Backend prerequisites: Java 11 and Maven 3.8+.

```bash
cd backend-master
mvn clean test
mvn clean package -DskipTests
```

Frontend prerequisites: Node.js 20.18.3 and npm 10.8.2.

```bash
cd spectrum-master
npm ci
cd proxy && npm ci && cd ..
npm run tsc -- --noEmit
npm run proxy-build
npm run build
```

Never place credentials in local properties, Compose files, source, test fixtures, or GitHub configuration. AWS deployments retrieve generated values from Secrets Manager at runtime.

## Deploy

The AWS resources, deployment workflow, cost controls, and operational commands are documented in [`infrastructure/aws/README.md`](infrastructure/aws/README.md). The GitHub Actions deployment uses short-lived OIDC credentials and SSM; no long-lived AWS key is stored in GitHub.

## Compatibility boundary

Some internal package names, persisted fields, collection names, and API contracts still contain the historical `syncari` token. They are not user-visible branding. They remain temporarily to preserve binary, database, and integration compatibility while `nextEdgeId` is written and exposed to users. Do not bulk-rename these internals without a versioned data and API migration.

## Security

See [`SECURITY.md`](SECURITY.md). Every push is scanned for secrets, production dependency critical findings, and build/test failures. Known legacy frontend dependencies are tracked separately and must be upgraded through tested component migrations rather than an unsafe forced audit rewrite.
