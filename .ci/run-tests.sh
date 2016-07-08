#!/usr/bin/env bash

set -e

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

# Test intermediate commits
echo -e "\nTesting each other intermediate commit between $MERGE_TARGET and HEAD..."
for sha in $( git rev-list origin/$MERGE_TARGET...HEAD | tail -n +2 ); do
    echo -e "\nTesting at $sha..."
    git show --summary $sha
    git checkout -q $sha
    if boot test; then
        echo -e "\nTests at $sha successful"
    else
        echo -e "\nTests on HEAD passed, however, tests failed on intermediate commit $sha."
        exit 1
    fi
done
