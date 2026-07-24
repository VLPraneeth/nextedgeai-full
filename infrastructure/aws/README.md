# NextEdge AI AWS environment

This stack provisions the approved single-node demo environment in Mumbai. It is intentionally isolated from pre-existing AWS workloads.

## Safety properties

- Dedicated VPC, subnet, route table, security group, IAM roles, Elastic IP, S3 bucket, and log groups.
- No SSH ingress. Administration uses AWS Systems Manager Session Manager.
- EC2 metadata requires IMDSv2.
- EBS and S3 data are encrypted; S3 public access is blocked.
- The EC2 role can invoke only the configured Bedrock model.
- A $100 monthly Bedrock budget sends warnings at 70% and 90%. At 100%, a Lambda sets `/nextedge-ai/llm/enabled=false` and adds an explicit Bedrock deny policy to the EC2 role.
- All resources are named and tagged for `NextEdgeAI`; existing instances are not referenced.

## Deploy

```powershell
$ami = aws ssm get-parameter --profile codex-cli --region ap-south-1 --name /aws/service/canonical/ubuntu/server/22.04/stable/current/amd64/hvm/ebs-gp2/ami-id --query Parameter.Value --output text
aws cloudformation deploy --profile codex-cli --region ap-south-1 --stack-name nextedge-ai-demo --template-file infrastructure/aws/nextedge-ai.yml --parameter-overrides UbuntuAmiId=$ami InstanceType=m6i.2xlarge --capabilities CAPABILITY_NAMED_IAM --tags Project=NextEdgeAI Environment=demo Confidential=true ManagedBy=CloudFormation
```

The budget warning SNS topic deliberately has no email subscriber because no notification address is stored in the repository. Add a confirmed private email subscription out-of-band.

## Deploy a validated application release

The runtime uses the instance IAM role and AWS Secrets Manager; it does not store cloud or model API keys in Git. On the managed EC2 host, run:

```bash
AWS_REGION=ap-south-1 ./infrastructure/aws/scripts/deploy-demo.sh /opt/nextedge-ai/current
```

The script writes a mode-`0600` environment file, keeps MongoDB and Redis on the private Docker network, starts prebuilt images, verifies both health endpoints, and performs a password-login smoke test without printing credentials.

## Emergency re-enable procedure

Only after reviewing Bedrock spend, remove the `NextEdgeAIBedrockEmergencyDeny` inline policy from `nextedge-ai-ec2-role` and set `/nextedge-ai/llm/enabled` back to `true`.
