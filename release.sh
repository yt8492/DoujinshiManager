#!/usr/bin/env bash

set -euo pipefail

readonly VERSION_FILE="gradle.properties"
readonly REMOTE="${RELEASE_REMOTE:-origin}"

fail() {
    echo "Error: $*" >&2
    exit 1
}

if [[ $# -ne 1 ]]; then
    fail "Usage: ./release.sh <tag>"
fi

readonly TAG="$1"

if [[ ! "$TAG" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
    fail "Tag must start with an alphanumeric character and contain only alphanumerics, '.', '_' or '-'."
fi

git rev-parse --show-toplevel >/dev/null 2>&1 || fail "Run this script inside the Git repository."
readonly REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPOSITORY_ROOT"

[[ -f "$VERSION_FILE" ]] || fail "$VERSION_FILE does not exist."
[[ -z "$(git status --porcelain)" ]] || fail "The working tree is not clean. Commit or stash changes first."

readonly BRANCH="$(git branch --show-current)"
[[ -n "$BRANCH" ]] || fail "Releases cannot be created from a detached HEAD."

git remote get-url "$REMOTE" >/dev/null 2>&1 || fail "Git remote '$REMOTE' is not configured."
git config user.name >/dev/null || fail "git user.name is not configured."
git config user.email >/dev/null || fail "git user.email is not configured."

git fetch --quiet "$REMOTE" "$BRANCH" --tags

if git show-ref --verify --quiet "refs/tags/$TAG"; then
    fail "Tag '$TAG' already exists."
fi

if git show-ref --verify --quiet "refs/remotes/$REMOTE/$BRANCH" &&
    ! git merge-base --is-ancestor "$REMOTE/$BRANCH" HEAD; then
    fail "The remote branch '$REMOTE/$BRANCH' contains commits that are not in the local branch."
fi

readonly VERSION_CODE_COUNT="$(grep -Ec '^VERSION_CODE=[0-9]+$' "$VERSION_FILE" || true)"
readonly VERSION_NAME_COUNT="$(grep -Ec '^VERSION_NAME=.*$' "$VERSION_FILE" || true)"
[[ "$VERSION_CODE_COUNT" -eq 1 ]] || fail "$VERSION_FILE must contain exactly one numeric VERSION_CODE."
[[ "$VERSION_NAME_COUNT" -eq 1 ]] || fail "$VERSION_FILE must contain exactly one VERSION_NAME."

readonly CURRENT_VERSION_CODE="$(sed -nE 's/^VERSION_CODE=([0-9]+)$/\1/p' "$VERSION_FILE")"
[[ "$CURRENT_VERSION_CODE" -lt 2100000000 ]] || fail "VERSION_CODE has reached the Google Play limit."
readonly NEXT_VERSION_CODE="$((CURRENT_VERSION_CODE + 1))"

VERSION_CODE="$NEXT_VERSION_CODE" perl -pi -e \
    's/^VERSION_CODE=[0-9]+$/VERSION_CODE=$ENV{VERSION_CODE}/' "$VERSION_FILE"
VERSION_NAME="$TAG" perl -pi -e \
    's/^VERSION_NAME=.*$/VERSION_NAME=$ENV{VERSION_NAME}/' "$VERSION_FILE"

git add "$VERSION_FILE"
git commit -m "アプリのバージョンを $TAG に更新"
git tag -a "$TAG" -m "リリース $TAG"

git push --atomic "$REMOTE" "HEAD:refs/heads/$BRANCH" "refs/tags/$TAG"

echo "Released $TAG (versionCode=$NEXT_VERSION_CODE)."
