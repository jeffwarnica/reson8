#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

QUAY_REGISTRY="${QUAY_REGISTRY:-quay.io}"
QUAY_NAMESPACE="${QUAY_NAMESPACE:-rhn_gps_jwarnica}"
APP_IMAGE_NAME="${APP_IMAGE_NAME:-reson8}"
DISSON8_IMAGE_NAME="${DISSON8_IMAGE_NAME:-disson8}"
BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-reson8-base}"
APP_TAG="${APP_TAG:-}"
DISSON8_TAG="${DISSON8_TAG:-}"
BASE_TAG="${BASE_TAG:-latest}"
SKIP_BASE_BUILD="${SKIP_BASE_BUILD:-0}"
NO_CACHE_BASE_BUILD="${NO_CACHE_BASE_BUILD:-1}"
PUSH_LATEST_ALIAS="${PUSH_LATEST_ALIAS:-0}"
DRY_RUN=0

usage() {
  cat <<'EOF'
Usage:
  deploy/publish-quay.sh --tag <app-tag> [options]

Options:
  --tag <tag>                 Runtime image tag to push (required unless APP_TAG is set).
  --disson8-tag <tag>         Optional disson8 runtime image tag (defaults to --tag).
  --base-tag <tag>            Base image tag (default: latest).
  --skip-base                 Skip base build/push and consume an existing base tag.
  --quay-namespace <name>     Quay namespace/org (default: rhn_gps_jwarnica).
  --quay-registry <host>      Quay registry host (default: quay.io).
  --also-latest               Also push runtime image as :latest.
  --dry-run                   Print commands without executing them.
  -h, --help                  Show this message.

Environment:
  QUAY_USERNAME and QUAY_PASSWORD (or robot account credentials) are required
  unless already logged in to Podman for the target Quay registry.

Examples:
  deploy/publish-quay.sh --tag v0.1.1
  deploy/publish-quay.sh --tag v0.1.1 --skip-base --base-tag stable
  deploy/publish-quay.sh --tag v0.1.1 --also-latest
EOF
}

run_cmd() {
  if [[ "${DRY_RUN}" == "1" ]]; then
    printf 'DRY-RUN:'
    printf ' %q' "$@"
    printf '\n'
    return
  fi
  "$@"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: required command not found: ${cmd}" >&2
    exit 1
  fi
}

check_quay_writable() {
  local probe_url="https://${QUAY_REGISTRY}/api/v1/repository/${QUAY_NAMESPACE}/${APP_IMAGE_NAME}"
  local response

  if ! response="$(curl -fsS -H 'Accept: application/json' "${probe_url}" 2>/dev/null)"; then
    echo "WARN: Unable to query Quay repository API (${probe_url}). Continuing." >&2
    return
  fi

  if [[ "${response}" == *'"state":"READ_ONLY"'* ]]; then
    echo "ERROR: Quay repository reports READ_ONLY state for ${QUAY_NAMESPACE}/${APP_IMAGE_NAME}." >&2
    echo "       Abort publish and retry when Quay returns to writable state." >&2
    exit 1
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --tag)
        APP_TAG="${2:-}"
        shift 2
        ;;
      --base-tag)
        BASE_TAG="${2:-}"
        shift 2
        ;;
      --disson8-tag)
        DISSON8_TAG="${2:-}"
        shift 2
        ;;
      --skip-base)
        SKIP_BASE_BUILD=1
        shift
        ;;
      --quay-namespace)
        QUAY_NAMESPACE="${2:-}"
        shift 2
        ;;
      --quay-registry)
        QUAY_REGISTRY="${2:-}"
        shift 2
        ;;
      --also-latest)
        PUSH_LATEST_ALIAS=1
        shift
        ;;
      --dry-run)
        DRY_RUN=1
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        echo "ERROR: unknown option: $1" >&2
        usage
        exit 1
        ;;
    esac
  done
}

ensure_login() {
  local auth_file="${XDG_RUNTIME_DIR:-/tmp}/containers/auth.json"
  local registry_key
  registry_key="${QUAY_REGISTRY}"

  if [[ -f "${auth_file}" ]] && grep -Fq "\"${registry_key}\"" "${auth_file}"; then
    return
  fi

  if [[ -z "${QUAY_USERNAME:-}" || -z "${QUAY_PASSWORD:-}" ]]; then
    echo "ERROR: Podman is not logged in to ${QUAY_REGISTRY} and QUAY_USERNAME/QUAY_PASSWORD are not set." >&2
    exit 1
  fi

  run_cmd podman login -u "${QUAY_USERNAME}" -p "${QUAY_PASSWORD}" "${QUAY_REGISTRY}"
}

main() {
  parse_args "$@"

  if [[ -z "${APP_TAG}" ]]; then
    echo "ERROR: --tag <app-tag> (or APP_TAG env var) is required." >&2
    usage
    exit 1
  fi

  require_cmd podman
  require_cmd curl
  require_cmd "${ROOT}/mvnw"

  if [[ -z "${DISSON8_TAG}" ]]; then
    DISSON8_TAG="${APP_TAG}"
  fi

  check_quay_writable
  ensure_login

  local base_image_ref="${QUAY_REGISTRY}/${QUAY_NAMESPACE}/${BASE_IMAGE_NAME}:${BASE_TAG}"
  local app_image_ref="${QUAY_REGISTRY}/${QUAY_NAMESPACE}/${APP_IMAGE_NAME}:${APP_TAG}"
  local disson8_image_ref="${QUAY_REGISTRY}/${QUAY_NAMESPACE}/${DISSON8_IMAGE_NAME}:${DISSON8_TAG}"
  local app_latest_ref="${QUAY_REGISTRY}/${QUAY_NAMESPACE}/${APP_IMAGE_NAME}:latest"

  cd "${ROOT}"

  if [[ "${SKIP_BASE_BUILD}" != "1" ]]; then
    local base_build_flags=("--pull=always")
    if [[ "${NO_CACHE_BASE_BUILD}" == "1" ]]; then
      base_build_flags+=("--no-cache")
    fi
    run_cmd podman build "${base_build_flags[@]}" \
      -f "${ROOT}/reson8/src/main/docker/Containerfile.gstreamer-base" \
      -t "${base_image_ref}" \
      "${ROOT}/reson8"
    run_cmd podman push "${base_image_ref}"
  else
    echo "Skipping base build; expecting pre-published base image: ${base_image_ref}"
  fi

  run_cmd "${ROOT}/mvnw" -pl reson8,disson8 -DskipTests package

  run_cmd podman build \
    --build-arg "BASE_IMAGE=${base_image_ref}" \
    -f "${ROOT}/reson8/src/main/docker/Dockerfile.jvm" \
    -t "${app_image_ref}" \
    "${ROOT}/reson8"
  run_cmd podman push "${app_image_ref}"

  run_cmd podman build \
    -f "${ROOT}/disson8/src/main/docker/Dockerfile.jvm" \
    -t "${disson8_image_ref}" \
    "${ROOT}/disson8"
  run_cmd podman push "${disson8_image_ref}"

  if [[ "${PUSH_LATEST_ALIAS}" == "1" ]]; then
    run_cmd podman tag "${app_image_ref}" "${app_latest_ref}"
    run_cmd podman push "${app_latest_ref}"
  fi

  echo "Publish complete:"
  echo "  base: ${base_image_ref}"
  echo "  app:      ${app_image_ref}"
  echo "  disson8:  ${disson8_image_ref}"
  if [[ "${PUSH_LATEST_ALIAS}" == "1" ]]; then
    echo "  app:      ${app_latest_ref}"
  fi
}

main "$@"
