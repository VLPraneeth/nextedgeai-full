# NextEdge AI security policy

## Confidentiality

This is a private commercial codebase. Report vulnerabilities privately to the repository owner. Do not create public issues, disclosures, forks, snippets, screenshots, or sample deployments.

## Secrets

- Store runtime secrets in AWS Secrets Manager.
- Use EC2 instance roles and GitHub OIDC instead of permanent AWS access keys.
- Never commit `.env` files, private keys, tokens, passwords, OAuth client secrets, or production data.
- Rotate any credential immediately if it appears in a working tree, log, artifact, commit, or CI output.
- Run Gitleaks against both the working tree and full history before release.

## Deployment controls

- AWS administration is through SSM; SSH is not publicly exposed.
- EC2 requires IMDSv2.
- Data volumes and snapshots are encrypted.
- Only the reverse proxy publishes a host port.
- Bedrock access is limited to the approved Nova Lite inference profile and protected by a kill switch, cost alerts, and an emergency IAM deny.
- The demo is single-node. Production requires private subnets, managed data services, TLS/DNS, a load balancer, multi-AZ recovery, and a formal availability plan.

## Dependency policy

CI fails on critical production npm advisories. The runtime proxy currently has no high or critical production advisories. The older browser application still contains high-severity advisories in major UI libraries; upgrading Ant Design, AG Grid, and VisX requires compatibility work and full regression testing. Do not use `npm audit fix --force` as a substitute for that migration.

## Supported release check

Before deployment:

```bash
gitleaks detect --source . --no-banner --redact
cd spectrum-master && npm audit --omit=dev --audit-level=critical
cd proxy && npm audit --omit=dev --audit-level=critical
cd ../../backend-master && mvn clean test
```

Then verify password login, tenant isolation, the five approved connectors, Bedrock, health endpoints, container restart recovery, monitoring, and backups.
