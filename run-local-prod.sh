#!/usr/bin/env bash
# Runs the app locally in prod-like mode (real Google OAuth, full Spring Security).
# Usage: ./run-local-prod.sh

set -e

ENV_FILE=".env.local"
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found. Copy .env.local and fill in your credentials."
  exit 1
fi

# Validate required values
missing=()
while IFS='=' read -r key value; do
  [[ "$key" =~ ^#|^$ ]] && continue
  if [ "$value" = "FILL_IN" ]; then
    missing+=("$key")
  fi
done < "$ENV_FILE"

if [ ${#missing[@]} -gt 0 ]; then
  echo "ERROR: Fill in these values in .env.local before running:"
  printf '  %s\n' "${missing[@]}"
  exit 1
fi

# Export env vars from .env.local
set -a
# shellcheck source=.env.local
source "$ENV_FILE"
set +a

echo ""
echo "▶  Building..."
./mvnw package -q -DskipTests

echo ""
echo "▶  Starting with profile: local-prod"
echo "   App URL:     http://localhost:8080"
echo "   H2 console:  http://localhost:8080/h2-console"
echo "   OAuth2 cb:   http://localhost:8080/login/oauth2/code/google"
echo ""

java -jar target/*.jar \
  --spring.profiles.active=local-prod
