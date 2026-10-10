#!/usr/bin/env bash
# Asks te-tengo-infra to deploy an API image BY DIGEST and waits for the result of that deployment.
# Used by produccion.yml (the verified candidate, after the merge to main) and rollback.yml (a released
# version). The approval happens in te-tengo-infra: its Deploy workflow (deploy.yml) pauses on its
# `produccion` environment until a reviewer approves, takes a database backup when the image changes,
# runs the Ansible app role over SSH, checks /actuator/health and checks that the running API container is
# this digest (a deploy that left another image running fails there). This script:
#   1. sends repository_dispatch `desplegar-api` with {digest, tag, version, ref, kind, request};
#      `request` ("api <run id>.<attempt>") goes into the infra run's name, so the run can be found;
#   2. finds that run and polls it (every TT_POLL_SECONDS, default 30) until it completes;
#   3. succeeds only if the run succeeded AND its deploy job ran (a job skipped by a switch is no deploy).
# Re-running a failed or timed-out job of the same workflow run first looks for a deploy it already
# requested: one still waiting or running is watched again, one that succeeded is not repeated, and only a
# failed or cancelled one is requested again.
#
# Environment:
#   DISPATCH_TOKEN  installation token of the GitHub App te-tengo-release-bot on Te-Tengo-Tech/te-tengo-infra
#                   with Contents: write, minted for this job (actions/create-github-app-token); only used
#                   to send the repository_dispatch                                            (required)
#   GH_TOKEN        the workflow's GITHUB_TOKEN: reads the runs of te-tengo-infra (a public repository).
#                   An App token expires after one hour, and the approval can take longer  (required)
#   DIGEST          sha256:<64 hex> of ghcr.io/te-tengo-tech/te-tengo-general-api              (required)
#   TAG             tag of that digest, shown on the host (e.g. 0.2.0-rc.1 or 0.1.0)            (required)
#   VERSION         x.y.z                                                                       (required)
#   REF             te-tengo-general-api commit behind the request                              (required)
#   KIND            release | rollback                                                          (default: release)
#   INFRA_REPO      owner/repository of the infra                         (default: Te-Tengo-Tech/te-tengo-infra)
#   TT_POLL_SECONDS seconds between two looks at the run                                        (default: 30)
#   GITHUB_RUN_ID, GITHUB_RUN_ATTEMPT, GITHUB_STEP_SUMMARY, GITHUB_OUTPUT: set by GitHub Actions.
# Output (GITHUB_OUTPUT): infra_run=<URL of the infra Deploy run>.
set -euo pipefail

INFRA_REPO="${INFRA_REPO:-Te-Tengo-Tech/te-tengo-infra}"
KIND="${KIND:-release}"
POLL="${TT_POLL_SECONDS:-30}"
RUN_ID="${GITHUB_RUN_ID:?GITHUB_RUN_ID is required}"
ATTEMPT="${GITHUB_RUN_ATTEMPT:-1}"
: "${DIGEST:?DIGEST is required}" "${TAG:?TAG is required}" "${VERSION:?VERSION is required}" "${REF:?REF is required}"

say() {
  echo "$*"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "$*" >> "$GITHUB_STEP_SUMMARY"; fi
}
fail() {
  echo "::error title=Production deploy::$*"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "**Failed:** $*" >> "$GITHUB_STEP_SUMMARY"; fi
  exit 1
}

if [ -z "${DISPATCH_TOKEN:-}" ]; then
  fail "No release bot token, so $INFRA_REPO cannot be asked to deploy $DIGEST. The job mints it from the organization variable RELEASE_APP_ID and secret RELEASE_APP_PRIVATE_KEY (docs/DEPLOYMENT.md)."
fi
: "${GH_TOKEN:?GH_TOKEN (the workflow token, to read the te-tengo-infra runs) is required}"
[[ "$DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || fail "Invalid digest: $DIGEST"
[[ "$KIND" =~ ^(release|rollback)$ ]] || fail "KIND must be release or rollback (found $KIND)"

# deploy.yml names a dispatched run "... [<request>]".
request="api $RUN_ID.$ATTEMPT"

# Newest infra Deploy run whose name contains $1, as "id|status|conclusion|url" (empty if none).
newest_run() {
  gh run list -R "$INFRA_REPO" --workflow deploy.yml --event repository_dispatch --limit 100 \
    --json databaseId,displayTitle,status,conclusion,url,createdAt \
    --jq "[.[] | select(.displayTitle | contains(\"$1\"))] | sort_by(.createdAt) | last
          | select(. != null) | \"\(.databaseId)|\(.status)|\(.conclusion)|\(.url)\""
}

# Conclusion of the deploy job of a run ("Deploy the app role (...)"): success only if it really ran.
deploy_job_conclusion() {
  gh run view "$1" -R "$INFRA_REPO" --json jobs \
    --jq '[.jobs[] | select(.name | startswith("Deploy the app role")) | .conclusion] | first // "none"'
}

run_id=""
previous=$(newest_run "[api $RUN_ID.") || fail "Cannot list the runs of $INFRA_REPO with the workflow token (is it still public?)."
if [ -n "$previous" ]; then
  IFS='|' read -r id status conclusion url <<<"$previous"
  if [ "$status" != completed ]; then
    run_id="$id"
    say "Watching the deploy this workflow run already requested: $url"
  elif [ "$conclusion" = success ] && [ "$(deploy_job_conclusion "$id")" = success ]; then
    say "This workflow run already deployed \`$DIGEST\` through $url; not requesting it again."
    if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "infra_run=$url" >> "$GITHUB_OUTPUT"; fi
    exit 0
  else
    echo "The previous request ($url) ended with '$conclusion': requesting the deploy again."
  fi
fi

if [ -z "$run_id" ]; then
  jq -n --arg digest "$DIGEST" --arg tag "$TAG" --arg version "$VERSION" --arg ref "$REF" \
    --arg kind "$KIND" --arg request "$request" \
    '{event_type: "desplegar-api",
      client_payload: {digest: $digest, tag: $tag, version: $version, ref: $ref, kind: $kind, request: $request}}' \
    | GH_TOKEN="$DISPATCH_TOKEN" gh api --method POST "repos/$INFRA_REPO/dispatches" --input - >/dev/null \
    || fail "The repository_dispatch to $INFRA_REPO was refused: the release bot needs Contents: write on it (App te-tengo-release-bot installed on $INFRA_REPO)."
  echo "Sent desplegar-api to $INFRA_REPO: $VERSION ($TAG), $DIGEST, request \"$request\"."
  for _ in $(seq 1 30); do
    sleep 10
    found=$(newest_run "[$request]") || fail "Cannot list the runs of $INFRA_REPO with the workflow token (is it still public?)."
    if [ -n "$found" ]; then
      IFS='|' read -r run_id status conclusion url <<<"$found"
      break
    fi
  done
  [ -n "$run_id" ] || fail "No Deploy run of $INFRA_REPO started for request \"$request\" within 5 minutes. Is deploy.yml (with the [request] run name) on its default branch?"
  say "te-tengo-infra Deploy run: $url"
fi
if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "infra_run=$url" >> "$GITHUB_OUTPUT"; fi

last=""
while :; do
  IFS='|' read -r status conclusion url < <(gh run view "$run_id" -R "$INFRA_REPO" --json status,conclusion,url \
    --jq '"\(.status)|\(.conclusion)|\(.url)"')
  if [ "$status" != "$last" ]; then
    case "$status" in
      waiting) echo "$(date -u +%H:%M:%SZ) waiting for a reviewer on te-tengo-infra's produccion environment: $url" ;;
      queued | pending) echo "$(date -u +%H:%M:%SZ) $status: waiting for a runner: $url" ;;
      *) echo "$(date -u +%H:%M:%SZ) $status" ;;
    esac
    last="$status"
  fi
  [ "$status" = completed ] && break
  sleep "$POLL"
done

[ "$conclusion" = success ] || fail "The te-tengo-infra deploy ended with '$conclusion': $url. Production was not changed or did not become healthy; nothing is tagged. Fix it and re-run the failed jobs (the same digest is requested again)."
job=$(deploy_job_conclusion "$run_id")
[ "$job" = success ] || fail "The te-tengo-infra run succeeded but its deploy job is '$job' (switched off or not configured?): $url"
say "Deployed \`$DIGEST\` ($VERSION, $TAG) to produccion: $url"
