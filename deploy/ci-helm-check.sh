#!/usr/bin/env bash
# Helm lint + template both appConfig postures + baseline manifest sanity.
# Used by CI (.github/workflows/ci.yml) and for local pre-PR checks.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="${ROOT}/helm/reson8"
PROD_VALUES="${CHART}/values-production.yaml"
TMPDIR_BASE="$(mktemp -d)"
trap 'rm -rf "${TMPDIR_BASE}"' EXIT

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  local label="$3"
  if ! grep -qE -- "${pattern}" "${file}"; then
    die "${label}: expected pattern not found: ${pattern}"
  fi
}

assert_platform_baseline() {
  local file="$1"
  local label="$2"
  assert_contains "${file}" '^[[:space:]]*resources:' "${label}: resources"
  assert_contains "${file}" 'runAsNonRoot:[[:space:]]*true' "${label}: runAsNonRoot"
  assert_contains "${file}" 'seccompProfile:' "${label}: seccompProfile"
  assert_contains "${file}" 'kind:[[:space:]]*NetworkPolicy' "${label}: NetworkPolicy"
}

require_cmd helm
require_cmd grep

echo "==> helm lint ${CHART}"
helm lint "${CHART}"

EVAL_OUT="${TMPDIR_BASE}/evaluator.yaml"
PROD_OUT="${TMPDIR_BASE}/production.yaml"

echo "==> helm template (evaluator / default)"
helm template reson8-ci "${CHART}" -n reson8-ci >"${EVAL_OUT}"
assert_platform_baseline "${EVAL_OUT}" "evaluator render"
assert_contains "${EVAL_OUT}" 'readiness-check:[[:space:]]*false' "evaluator render: readiness-check false"

echo "==> helm template (production / -f values-production.yaml)"
[[ -f "${PROD_VALUES}" ]] || die "missing ${PROD_VALUES}"
helm template reson8-ci "${CHART}" -n reson8-ci -f "${PROD_VALUES}" >"${PROD_OUT}"
assert_platform_baseline "${PROD_OUT}" "production render"
assert_contains "${PROD_OUT}" 'readiness-check:[[:space:]]*true' "production render: readiness-check true"
assert_contains "${PROD_OUT}" 'viewer-groups:' "production render: viewer-groups"

echo "OK: helm lint + evaluator/production template sanity passed."
