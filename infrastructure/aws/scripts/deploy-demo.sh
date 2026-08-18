#!/usr/bin/env bash
set -euo pipefail

release_dir="${1:?usage: deploy-demo.sh /opt/nextedge-ai/releases/<release>}"
release_dir="$(readlink -f "$release_dir")"
release_tag="${2:-${NEXTEDGE_RELEASE_TAG:-94b12b8}}"
region="${AWS_REGION:-ap-south-1}"
runtime_dir="/opt/nextedge-ai/runtime"

test -f "$release_dir/docker-compose.yml"
install -d -m 0700 "$runtime_dir"

admin_json="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/admin --query SecretString --output text)"
tenant_admin_json="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/tenant-admin --query SecretString --output text)"
mongo_json="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/mongodb --query SecretString --output text)"
jwt_secret="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/jwt --query SecretString --output text)"
redis_secret="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/redis --query SecretString --output text)"
connector_json="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/connectors --query SecretString --output text)"
google_auth_json="$(aws secretsmanager get-secret-value --region "$region" --secret-id nextedge-ai/demo/google-auth --query SecretString --output text 2>/dev/null || printf '{}')"

admin_email="$(jq -r '.username' <<<"$admin_json")"
admin_password="$(jq -r '.password' <<<"$admin_json")"
tenant_admin_email="$(jq -r '.username' <<<"$tenant_admin_json")"
tenant_admin_password="$(jq -r '.password' <<<"$tenant_admin_json")"
mongo_username="$(jq -r '.username' <<<"$mongo_json")"
mongo_password="$(jq -r '.password' <<<"$mongo_json")"
connector_username="$(jq -r '.username' <<<"$connector_json")"
connector_password="$(jq -r '.password' <<<"$connector_json")"
google_client_id="$(jq -r '.client_id // empty' <<<"$google_auth_json")"
google_demo_email="$(jq -r '.demo_email // empty' <<<"$google_auth_json")"

test "$admin_email" != "null"
test "${#admin_password}" -ge 16
test "$tenant_admin_email" != "null"
test "${#tenant_admin_password}" -ge 16
test "$mongo_username" != "null"
test "${#mongo_password}" -ge 16
test "${#jwt_secret}" -ge 32
test "${#redis_secret}" -ge 16
test "$connector_username" != "null"
test "${#connector_password}" -ge 16

umask 077
env_file="$(mktemp "$runtime_dir/.env.XXXXXX")"
trap 'rm -f "$env_file"' EXIT
printf '%s\n' \
  "NEXTEDGE_ADMIN_EMAIL=$admin_email" \
  "NEXTEDGE_ADMIN_PASSWORD=$admin_password" \
  "NEXTEDGE_TENANT_ADMIN_EMAIL=$tenant_admin_email" \
  "NEXTEDGE_TENANT_ADMIN_PASSWORD=$tenant_admin_password" \
  "NEXTEDGE_MONGO_USERNAME=$mongo_username" \
  "NEXTEDGE_MONGO_PASSWORD=$mongo_password" \
  "NEXTEDGE_REDIS_PASSWORD=$redis_secret" \
  "NEXTEDGE_CONNECTOR_DB_USERNAME=$connector_username" \
  "NEXTEDGE_CONNECTOR_DB_PASSWORD=$connector_password" \
  "NEXTEDGE_GOOGLE_CLIENT_ID=$google_client_id" \
  "NEXTEDGE_GOOGLE_DEMO_EMAIL=$google_demo_email" \
  "NEXTEDGE_JWT_SECURITY_SECRET=$jwt_secret" \
  "NEXTEDGE_RELEASE_TAG=$release_tag" \
  "NEXTEDGE_BUILD_SHA=$release_tag" \
  "NEXTEDGE_BUILD_BRANCH=${NEXTEDGE_BUILD_BRANCH:-main}" >"$env_file"
chmod 0600 "$env_file"
mv -f "$env_file" "$runtime_dir/.env"
trap - EXIT

# Docker Compose gives exported shell variables precedence over --env-file.
# Re-export the freshly fetched values so a build shell cannot leak stale credentials into a deployment.
export NEXTEDGE_ADMIN_EMAIL="$admin_email"
export NEXTEDGE_ADMIN_PASSWORD="$admin_password"
export NEXTEDGE_TENANT_ADMIN_EMAIL="$tenant_admin_email"
export NEXTEDGE_TENANT_ADMIN_PASSWORD="$tenant_admin_password"
export NEXTEDGE_MONGO_USERNAME="$mongo_username"
export NEXTEDGE_MONGO_PASSWORD="$mongo_password"
export NEXTEDGE_REDIS_PASSWORD="$redis_secret"
export NEXTEDGE_CONNECTOR_DB_USERNAME="$connector_username"
export NEXTEDGE_CONNECTOR_DB_PASSWORD="$connector_password"
export NEXTEDGE_GOOGLE_CLIENT_ID="$google_client_id"
export NEXTEDGE_GOOGLE_DEMO_EMAIL="$google_demo_email"
export NEXTEDGE_JWT_SECURITY_SECRET="$jwt_secret"
export NEXTEDGE_RELEASE_TAG="$release_tag"
export NEXTEDGE_BUILD_SHA="$release_tag"

ln -sfn "$release_dir" /opt/nextedge-ai/current
cd /opt/nextedge-ai/current

docker compose --project-name nextedge-ai --env-file "$runtime_dir/.env" config >/dev/null
docker compose --project-name nextedge-ai --env-file "$runtime_dir/.env" up -d --no-build --force-recreate

for attempt in $(seq 1 60); do
  if curl --fail --silent http://127.0.0.1/version >/dev/null \
    && curl --fail --silent http://127.0.0.1/arcade/health >/dev/null; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    docker compose --project-name nextedge-ai --env-file "$runtime_dir/.env" ps
    exit 1
  fi
  sleep 5
done

check_login() {
  local email="$1"
  local password="$2"
  local headers_file
  local cookie_file
  local xsrf_token
  local status
  headers_file="$(mktemp "$runtime_dir/login-headers.XXXXXX")"
  cookie_file="$(mktemp "$runtime_dir/login-cookies.XXXXXX")"
  curl --fail --silent --cookie-jar "$cookie_file" http://127.0.0.1/ >/dev/null
  xsrf_token="$(awk '$6 == "x-xsrf-token" { print $7 }' "$cookie_file" | tail -n 1)"
  test -n "$xsrf_token"
  status="$(curl --silent --show-error --output /dev/null --dump-header "$headers_file" \
    --write-out '%{http_code}' --request POST \
    --cookie "$cookie_file" \
    --header "x-xsrf-token: $xsrf_token" \
    --data-urlencode "username=$email" \
    --data-urlencode "password=$password" \
    http://127.0.0.1/arcade/api/v1/authenticate)"
  test "$status" = "200"
  grep -qi '^Authorization: Bearer ' "$headers_file"
  rm -f "$headers_file" "$cookie_file"
}

check_login "$admin_email" "$admin_password"
check_login "$tenant_admin_email" "$tenant_admin_password"

docker compose --project-name nextedge-ai --env-file "$runtime_dir/.env" ps
echo "NEXTEDGE_DEMO_READY"
