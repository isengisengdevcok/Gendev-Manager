#!/usr/bin/env bash
set -euo pipefail

# Automates generating a unique RSA-2048 Android release keystore and
# configuring the 4 GitHub Actions secrets required for signed releases:
#   - ANDROID_KEYSTORE_BASE64
#   - ANDROID_KEYSTORE_PASSWORD
#   - ANDROID_KEY_ALIAS
#   - ANDROID_KEY_PASSWORD

SIGNING_DIR=".signing"
KEYSTORE_FILE="${SIGNING_DIR}/gendev-release.jks"
KEY_ALIAS="gendev_release"
SUMMARY_FILE="${SIGNING_DIR}/secrets_backup.txt"

mkdir -p "$SIGNING_DIR"

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: 'keytool' (JDK) is required. Install JDK 17+ or JDK 21 and retry."
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: 'python3' is required to generate random credentials and encode base64."
  exit 1
fi

PASSWORD=$(python3 - <<'PY'
import secrets, string
alphabet = string.ascii_letters + string.digits
print("".join(secrets.choice(alphabet) for _ in range(24)))
PY
)

if [ -f "$KEYSTORE_FILE" ]; then
  echo "Existing keystore found at ${KEYSTORE_FILE}."
  echo "Remove it first if you intend to generate a brand-new signing identity."
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -alias "$KEY_ALIAS" \
  -keypass "$PASSWORD" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=GENDEV MANAGER, O=GENDEV, C=ID" >/dev/null

BASE64_KEYSTORE=$(python3 - "$KEYSTORE_FILE" <<'PY'
import base64, sys
with open(sys.argv[1], "rb") as f:
    print(base64.b64encode(f.read()).decode("ascii"), end="")
PY
)

cat > "$SUMMARY_FILE" <<EOF
# KEEP THIS FILE PRIVATE — DO NOT COMMIT TO GIT (.signing/ is in .gitignore)
ANDROID_KEY_ALIAS=${KEY_ALIAS}
ANDROID_KEYSTORE_PASSWORD=${PASSWORD}
ANDROID_KEY_PASSWORD=${PASSWORD}
ANDROID_KEYSTORE_BASE64=${BASE64_KEYSTORE}
EOF
chmod 600 "$KEYSTORE_FILE" "$SUMMARY_FILE"

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  REPO_SLUG="${1:-}"
  REPO_FLAG=()
  if [ -n "$REPO_SLUG" ]; then
    REPO_FLAG=(--repo "$REPO_SLUG")
  fi

  printf '%s' "$BASE64_KEYSTORE" | gh secret set ANDROID_KEYSTORE_BASE64 "${REPO_FLAG[@]}"
  printf '%s' "$PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD "${REPO_FLAG[@]}"
  printf '%s' "$KEY_ALIAS" | gh secret set ANDROID_KEY_ALIAS "${REPO_FLAG[@]}"
  printf '%s' "$PASSWORD" | gh secret set ANDROID_KEY_PASSWORD "${REPO_FLAG[@]}"

  echo "SUCCESS: All 4 GitHub repository secrets have been configured automatically."
  echo "Keystore backup saved locally at ${KEYSTORE_FILE} (gitignored)."
  echo "Next: push to main/master or run 'gh workflow run android-release.yml' to publish a signed Release APK."
else
  echo "Keystore generated cleanly at ${KEYSTORE_FILE} (gitignored)."
  echo "GitHub CLI ('gh') is not authenticated here, so secrets were saved to:"
  echo "  ${SUMMARY_FILE}"
  echo "Either run 'gh auth login' and re-apply with 'gh secret set', or copy those 4 values into:"
  echo "  GitHub Repository -> Settings -> Secrets and variables -> Actions"
fi
