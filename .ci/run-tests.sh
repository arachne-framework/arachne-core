#!/usr/bin/env bash

set -e

# echo "debugging..."

# echo "git status (current)"
# git status
# git rev-list HEAD

# echo "git status (master)"
# git checkout master
# git status
# git rev-list HEAD

# echo "git status (origin/master)"
# git checkout origin/master
# git status
# git rev-list HEAD

# Run tests
echo "running tests..."
boot test -j junit 2>&1

# Copy junit report
if [ -z "$CIRCLE_TEST_REPORTS" ]; then
    echo "Skipping junit report copy"
else
    echo "Copying junit report..."
    mkdir -p $CIRCLE_TEST_REPORTS/clojure-test/
    cp -r target/junit/* $CIRCLE_TEST_REPORTS/clojure-test/
fi

# Exit if not in PR context
if [ -z "$CI_PULL_REQUESTS" ]; then
    exit 0;
fi

export MERGE_TARGET=master # hardcoded for now

# Test a hypothetical merge
echo -e "\nTesting result of hypothetical merge with $MERGE_TARGET..."

# Necessary so Git doesn't barf later on...
git config user.email "ci@arachne-framework.org"
git config user.name "Arachne CI"

git fetch origin $MERGE_TARGET
if git merge --no-commit origin/$MERGE_TARGET; then
    if boot test; then
        echo -e "Hypothetical merge successful. Resetting GIT repository..."
        git stash
    else
        echo -e "\nTests on HEAD passed, however, tests failed after hypothetical merge with $MERGE_TARGET"
        exit 1
    fi
else
    echo -e "\nTests on HEAD passed, however, the branch could not be cleanly merged with $MERGE_TARGET"
    exit 1
fi


# Test intermediate commits
echo -e "\nTesting each intermediate commit between $MERGE_TARGET and HEAD..."
for sha in $( git rev-list origin/$MERGE_TARGET...HEAD ); do
    echo -e "\nTesting at $sha..."
    git checkout -q $sha
    if boot test; then
        echo -e "\nTests at $sha successful"
    else
        echo -e "\nTests on HEAD passed, however, tests failed on intermediate commit $sha."
        exit 1
    fi
done
