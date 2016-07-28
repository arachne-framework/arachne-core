#!/usr/bin/env bash

set -e

if [ -e ~/.m2/repository/org/arachne-framework/arachne-buildtools/$ARACHNE_BUILDTOOLS_VERSION ];
then
    echo "Buildtools lib already installed..."
else
    echo "Installing buildtools..."
    git clone git@github.com:arachne-framework/arachne-buildtools.git
    cd arachne-buildtools
    git checkout $ARACHNE_BUILDTOOLS_VERSION
    boot build
fi
