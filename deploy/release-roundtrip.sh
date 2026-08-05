#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MODE=""

APP_TAG="${APP_TAG:-}"
BASE_TAG="${BASE_TAG:-latest}"
CHART_VERSION_OVERRIDE="${CHART_VERSION_OVERRIDE:-}"
CHART_APP_VERSION_OVERRIDE="${CHART_APP_VERSION_OVERRIDE:-}"
CHART_IMAGE_TAG_OVERRIDE="${CHART_IMAGE_TAG_OVERRIDE:-}"
CHART_DISSON8_IMAGE_TAG_OVERRIDE="${CHART_DISSON8_IMAGE_TAG_OVERRIDE:-}"

QUAY_REGISTRY="${QUAY_REGISTRY:-quay.io}"
QUAY_NAMESPACE="${QUAY_NAMESPACE:-rhn_gps_jwarnica}"

SKIP_BASE_BUILD=0
PUSH_LATEST_ALIAS=0
PUSH_CHART=0
DRY_RUN=0
STRICT_MODE=0

if [[ "${CI:-}" =~ ^(1|true|TRUE|yes|YES)$ ]]; then
  STRICT_MODE=1
fi

ROOT_POM="${ROOT}/pom.xml"
RESON8_POM="${ROOT}/reson8/pom.xml"
DISSON8_POM="${ROOT}/disson8/pom.xml"
CHART_FILE="${ROOT}/helm/reson8/Chart.yaml"
VALUES_FILE="${ROOT}/helm/reson8/values.yaml"
PUBLISH_SCRIPT="${ROOT}/deploy/publish-quay.sh"

ROOT_MAVEN_VERSION=""
RESON8_PARENT_VERSION=""
DISSON8_PARENT_VERSION=""
CHART_NAME=""
CHART_VERSION=""
CHART_APP_VERSION=""
VALUES_IMAGE_TAG=""
VALUES_DISSON8_IMAGE_TAG=""

usage() {
  cat <<'EOF'
Usage:
  deploy/release-roundtrip.sh <mode> [options]

Modes:
  snapshot       Publish runtime image (fast lane, mutable tags allowed).
  release        Publish runtime image (release checks enabled).
  chart-release  Package and push Helm chart to Quay OCI.

Options:
  --app-tag <tag>            Runtime image tag for publish-quay.sh.
  --base-tag <tag>           Base image tag (default: latest).
  --skip-base                Skip base image build/push.
  --also-latest              Also push runtime image as :latest.
  --chart-version <version>  Require Chart.yaml version to match this value.
  --chart-app-version <ver>  Require Chart.yaml appVersion to match this value.
  --chart-image-tag <tag>    Require values.yaml image.tag to match this value.
  --chart-disson8-image-tag <tag>
                            Require values.yaml disson8.image.tag to match this value.
  --push-chart               Package and push chart to oci://<registry>/<namespace>.
  --quay-registry <host>     Registry host (default: quay.io).
  --quay-namespace <name>    Quay namespace/org (default: rhn_gps_jwarnica).
  --strict                   Treat warnings as errors (default in CI=true).
  --dry-run                  Print commands without executing them.
  -h, --help                 Show this help message.

Examples:
  deploy/release-roundtrip.sh snapshot --app-tag latest --skip-base --also-latest
  deploy/release-roundtrip.sh release --app-tag v0.1.1 --skip-base --push-chart
  deploy/release-roundtrip.sh chart-release --push-chart --chart-version 0.1.1
EOF
}

is_true() {
  local value="${1:-}"
  [[ "${value}" == "1" || "${value}" == "true" || "${value}" == "TRUE" || "${value}" == "yes" || "${value}" == "YES" ]]
}

log_info() {
  echo "INFO: $*"
}

log_warn() {
  echo "WARN: $*" >&2
}

log_error() {
  echo "ERROR: $*" >&2
}

warn_or_fail() {
  local message="$1"
  if [[ "${STRICT_MODE}" == "1" ]]; then
    log_error "${message}"
    exit 3
  fi
  log_warn "${message}"
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
    log_error "required command not found: ${cmd}"
    exit 2
  fi
}

require_file() {
  local path="$1"
  if [[ ! -f "${path}" ]]; then
    log_error "required file not found: ${path}"
    exit 2
  fi
}

strip_quotes() {
  local value="${1:-}"
  value="${value%\"}"
  value="${value#\"}"
  echo "${value}"
}

extract_first_project_version() {
  local file="$1"
  awk '
    /<version>[^<]+<\/version>/ {
      line = $0
      sub(/^.*<version>/, "", line)
      sub(/<\/version>.*$/, "", line)
      print line
      exit
    }
  ' "${file}"
}

extract_parent_version() {
  local file="$1"
  awk '
    /<parent>/ { in_parent = 1; next }
    /<\/parent>/ { in_parent = 0; next }
    in_parent && /<version>[^<]+<\/version>/ {
      line = $0
      sub(/^.*<version>/, "", line)
      sub(/<\/version>.*$/, "", line)
      print line
      exit
    }
  ' "${file}"
}

extract_chart_field() {
  local key="$1"
  awk -v key="${key}" '
    $0 ~ ("^" key ":[[:space:]]*") {
      line = $0
      sub("^" key ":[[:space:]]*", "", line)
      print line
      exit
    }
  ' "${CHART_FILE}"
}

extract_values_image_tag() {
  awk '
    /^image:[[:space:]]*$/ { in_image = 1; next }
    in_image && /^[^[:space:]]/ { in_image = 0 }
    in_image && /^[[:space:]]+tag:[[:space:]]*/ {
      line = $0
      sub(/^[[:space:]]+tag:[[:space:]]*/, "", line)
      print line
      exit
    }
  ' "${VALUES_FILE}"
}

extract_values_disson8_image_tag() {
  awk '
    /^disson8:[[:space:]]*$/ { in_disson8 = 1; next }
    in_disson8 && /^[^[:space:]]/ { in_disson8 = 0 }
    in_disson8 && /^[[:space:]]+image:[[:space:]]*$/ { in_image = 1; next }
    in_disson8 && in_image && /^[[:space:]]{2}[^[:space:]]/ { in_image = 0 }
    in_disson8 && in_image && /^[[:space:]]+tag:[[:space:]]*/ {
      line = $0
      sub(/^[[:space:]]+tag:[[:space:]]*/, "", line)
      print line
      exit
    }
  ' "${VALUES_FILE}"
}

load_metadata() {
  require_file "${ROOT_POM}"
  require_file "${RESON8_POM}"
  require_file "${DISSON8_POM}"
  require_file "${CHART_FILE}"
  require_file "${VALUES_FILE}"

  ROOT_MAVEN_VERSION="$(extract_first_project_version "${ROOT_POM}")"
  RESON8_PARENT_VERSION="$(extract_parent_version "${RESON8_POM}")"
  DISSON8_PARENT_VERSION="$(extract_parent_version "${DISSON8_POM}")"

  CHART_NAME="$(strip_quotes "$(extract_chart_field "name")")"
  CHART_VERSION="$(strip_quotes "$(extract_chart_field "version")")"
  CHART_APP_VERSION="$(strip_quotes "$(extract_chart_field "appVersion")")"
  VALUES_IMAGE_TAG="$(strip_quotes "$(extract_values_image_tag)")"
  VALUES_DISSON8_IMAGE_TAG="$(strip_quotes "$(extract_values_disson8_image_tag)")"

  if [[ -z "${ROOT_MAVEN_VERSION}" || -z "${RESON8_PARENT_VERSION}" || -z "${DISSON8_PARENT_VERSION}" ]]; then
    log_error "failed to resolve Maven versions from pom.xml files."
    exit 3
  fi
  if [[ -z "${CHART_NAME}" || -z "${CHART_VERSION}" || -z "${CHART_APP_VERSION}" ]]; then
    log_error "failed to resolve chart metadata from ${CHART_FILE}."
    exit 3
  fi
  if [[ -z "${VALUES_IMAGE_TAG}" ]]; then
    log_error "failed to resolve image.tag from ${VALUES_FILE}."
    exit 3
  fi
  if [[ -z "${VALUES_DISSON8_IMAGE_TAG}" ]]; then
    log_error "failed to resolve disson8.image.tag from ${VALUES_FILE}."
    exit 3
  fi
}

validate_common_consistency() {
  if [[ "${RESON8_PARENT_VERSION}" != "${ROOT_MAVEN_VERSION}" ]]; then
    warn_or_fail "reson8/pom.xml parent version (${RESON8_PARENT_VERSION}) differs from root pom version (${ROOT_MAVEN_VERSION})."
  fi
  if [[ "${DISSON8_PARENT_VERSION}" != "${ROOT_MAVEN_VERSION}" ]]; then
    warn_or_fail "disson8/pom.xml parent version (${DISSON8_PARENT_VERSION}) differs from root pom version (${ROOT_MAVEN_VERSION})."
  fi

  if [[ -n "${CHART_VERSION_OVERRIDE}" && "${CHART_VERSION}" != "${CHART_VERSION_OVERRIDE}" ]]; then
    warn_or_fail "Chart.yaml version (${CHART_VERSION}) does not match requested --chart-version (${CHART_VERSION_OVERRIDE})."
  fi
  if [[ -n "${CHART_APP_VERSION_OVERRIDE}" && "${CHART_APP_VERSION}" != "${CHART_APP_VERSION_OVERRIDE}" ]]; then
    warn_or_fail "Chart.yaml appVersion (${CHART_APP_VERSION}) does not match requested --chart-app-version (${CHART_APP_VERSION_OVERRIDE})."
  fi
  if [[ -n "${CHART_IMAGE_TAG_OVERRIDE}" && "${VALUES_IMAGE_TAG}" != "${CHART_IMAGE_TAG_OVERRIDE}" ]]; then
    warn_or_fail "values.yaml image.tag (${VALUES_IMAGE_TAG}) does not match requested --chart-image-tag (${CHART_IMAGE_TAG_OVERRIDE})."
  fi
  if [[ -n "${CHART_DISSON8_IMAGE_TAG_OVERRIDE}" && "${VALUES_DISSON8_IMAGE_TAG}" != "${CHART_DISSON8_IMAGE_TAG_OVERRIDE}" ]]; then
    warn_or_fail "values.yaml disson8.image.tag (${VALUES_DISSON8_IMAGE_TAG}) does not match requested --chart-disson8-image-tag (${CHART_DISSON8_IMAGE_TAG_OVERRIDE})."
  fi
}

validate_snapshot_mode() {
  if [[ -z "${APP_TAG}" ]]; then
    log_error "--app-tag is required for mode 'snapshot'."
    exit 2
  fi
  if [[ "${CHART_APP_VERSION}" != "${ROOT_MAVEN_VERSION}" ]]; then
    log_warn "Chart appVersion (${CHART_APP_VERSION}) differs from Maven version (${ROOT_MAVEN_VERSION})."
  fi
}

validate_release_mode() {
  if [[ -z "${APP_TAG}" ]]; then
    log_error "--app-tag is required for mode 'release'."
    exit 2
  fi
  if [[ "${ROOT_MAVEN_VERSION}" == *-SNAPSHOT ]]; then
    warn_or_fail "release mode expects non-SNAPSHOT Maven version (found ${ROOT_MAVEN_VERSION})."
  fi
  if [[ "${APP_TAG}" == "latest" || "${APP_TAG}" == "snapshot" ]]; then
    warn_or_fail "release mode expects an immutable app tag (got '${APP_TAG}')."
  fi
  if [[ "${CHART_APP_VERSION}" != "${ROOT_MAVEN_VERSION}" ]]; then
    warn_or_fail "release mode expects Chart.yaml appVersion (${CHART_APP_VERSION}) to match Maven version (${ROOT_MAVEN_VERSION})."
  fi

  local expected_chart_tag="${CHART_IMAGE_TAG_OVERRIDE:-${APP_TAG}}"
  if [[ "${PUSH_CHART}" == "1" && "${VALUES_IMAGE_TAG}" != "${expected_chart_tag}" ]]; then
    warn_or_fail "release + --push-chart expects values.yaml image.tag (${VALUES_IMAGE_TAG}) to match ${expected_chart_tag}."
  fi
  if [[ "${PUSH_CHART}" == "1" && "${VALUES_DISSON8_IMAGE_TAG}" != "${expected_chart_tag}" ]]; then
    warn_or_fail "release + --push-chart expects values.yaml disson8.image.tag (${VALUES_DISSON8_IMAGE_TAG}) to match ${expected_chart_tag}."
  fi
}

validate_chart_release_mode() {
  if [[ "${PUSH_CHART}" != "1" ]]; then
    log_error "mode 'chart-release' requires --push-chart."
    exit 2
  fi
  if [[ -z "${CHART_VERSION}" || -z "${CHART_APP_VERSION}" ]]; then
    log_error "Chart.yaml must define both version and appVersion."
    exit 3
  fi
  if [[ "${VALUES_IMAGE_TAG}" == "latest" ]]; then
    warn_or_fail "chart-release should not default to mutable image.tag=latest."
  fi
  if [[ "${VALUES_DISSON8_IMAGE_TAG}" == "latest" ]]; then
    warn_or_fail "chart-release should not default to mutable disson8.image.tag=latest."
  fi
  if [[ "${CHART_APP_VERSION}" != "${ROOT_MAVEN_VERSION}" ]]; then
    warn_or_fail "chart-release expects Chart.yaml appVersion (${CHART_APP_VERSION}) to match Maven version (${ROOT_MAVEN_VERSION})."
  fi
}

run_publish_quay() {
  if [[ ! -x "${PUBLISH_SCRIPT}" ]]; then
    log_error "publish script is missing or not executable: ${PUBLISH_SCRIPT}"
    exit 2
  fi

  local args=(
    "--tag" "${APP_TAG}"
    "--base-tag" "${BASE_TAG}"
    "--quay-namespace" "${QUAY_NAMESPACE}"
    "--quay-registry" "${QUAY_REGISTRY}"
  )

  if [[ "${SKIP_BASE_BUILD}" == "1" ]]; then
    args+=("--skip-base")
  fi
  if [[ "${PUSH_LATEST_ALIAS}" == "1" ]]; then
    args+=("--also-latest")
  fi
  if [[ "${DRY_RUN}" == "1" ]]; then
    args+=("--dry-run")
  fi

  run_cmd "${PUBLISH_SCRIPT}" "${args[@]}"
}

run_helm_publish() {
  require_cmd helm

  local chart_ref="oci://${QUAY_REGISTRY}/${QUAY_NAMESPACE}"
  local chart_archive="${ROOT}/helm/reson8/${CHART_NAME}-${CHART_VERSION}.tgz"

  if [[ -n "${QUAY_USERNAME:-}" && -n "${QUAY_PASSWORD:-}" ]]; then
    if [[ "${DRY_RUN}" == "1" ]]; then
      echo "DRY-RUN: helm registry login ${QUAY_REGISTRY} -u <redacted> --password-stdin"
    else
      printf '%s' "${QUAY_PASSWORD}" | helm registry login "${QUAY_REGISTRY}" -u "${QUAY_USERNAME}" --password-stdin
    fi
  else
    log_info "QUAY_USERNAME/QUAY_PASSWORD not set; assuming Helm registry login already exists."
  fi

  run_cmd helm package "${ROOT}/helm/reson8" --destination "${ROOT}/helm/reson8"

  if [[ "${DRY_RUN}" != "1" && ! -f "${chart_archive}" ]]; then
    log_error "expected chart archive not found after packaging: ${chart_archive}"
    exit 4
  fi

  run_cmd helm push "${chart_archive}" "${chart_ref}"
}

parse_args() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 2
  fi

  MODE="$1"
  shift

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --app-tag)
        APP_TAG="${2:-}"
        shift 2
        ;;
      --base-tag)
        BASE_TAG="${2:-}"
        shift 2
        ;;
      --skip-base)
        SKIP_BASE_BUILD=1
        shift
        ;;
      --also-latest)
        PUSH_LATEST_ALIAS=1
        shift
        ;;
      --chart-version)
        CHART_VERSION_OVERRIDE="${2:-}"
        shift 2
        ;;
      --chart-app-version)
        CHART_APP_VERSION_OVERRIDE="${2:-}"
        shift 2
        ;;
      --chart-image-tag)
        CHART_IMAGE_TAG_OVERRIDE="${2:-}"
        shift 2
        ;;
      --chart-disson8-image-tag)
        CHART_DISSON8_IMAGE_TAG_OVERRIDE="${2:-}"
        shift 2
        ;;
      --push-chart)
        PUSH_CHART=1
        shift
        ;;
      --quay-registry)
        QUAY_REGISTRY="${2:-}"
        shift 2
        ;;
      --quay-namespace)
        QUAY_NAMESPACE="${2:-}"
        shift 2
        ;;
      --strict)
        STRICT_MODE=1
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
        log_error "unknown option: $1"
        usage
        exit 2
        ;;
    esac
  done
}

main() {
  parse_args "$@"
  load_metadata
  validate_common_consistency

  case "${MODE}" in
    snapshot)
      validate_snapshot_mode
      run_publish_quay
      if [[ "${PUSH_CHART}" == "1" ]]; then
        run_helm_publish
      fi
      ;;
    release)
      validate_release_mode
      run_publish_quay
      if [[ "${PUSH_CHART}" == "1" ]]; then
        run_helm_publish
      fi
      ;;
    chart-release)
      validate_chart_release_mode
      run_helm_publish
      ;;
    *)
      log_error "unknown mode '${MODE}'. Expected one of: snapshot, release, chart-release."
      usage
      exit 2
      ;;
  esac

  log_info "release-roundtrip completed for mode '${MODE}'."
}

main "$@"
