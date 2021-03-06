#!/bin/bash

# The master for this script exists in the Python '2.7' directory. Do
# not edit the version of this script found in other directories. When
# the version of the script in the Python '2.7' directory is modified,
# it must then be be copied into other directories. This is necessary as
# Docker when building an image cannot copy in a file from outside of
# the directory where the Dockerfile resides.

# Record everything that is run from this script so appears in logs.

set -x

# Ensure that any failure within this script causes this script to fail
# immediately. This eliminates the need to check individual statuses for
# anything which is run and prematurely exit. Note that the feature of
# bash to exit in this way isn't foolproof. Ensure that you heed any
# advice in:
#
#   http://mywiki.wooledge.org/BashFAQ/105
#   http://fvue.nl/wiki/Bash:_Error_handling
#
# and use best practices to ensure that failures are always detected.
# Any user supplied scripts should also use this failure mode.

set -eo pipefail

# Add our user and group first to make sure their IDs get assigned consistently, regardless of whatever dependencies get added
groupadd -r compute
useradd -r -g compute compute
mkdir /home/compute
chown compute:compute /home/compute

# Grab gosu for easy step-down from root
GOSU_VERSION=1.10
GOSU_DOWNLOAD_URL="https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-amd64"
GOSU_DOWNLOAD_SIG="https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-amd64.asc"
GOSU_DOWNLOAD_KEY="0x036A9C25BF357DD4"

# Download and install gosu
# https://github.com/tianon/gosu/releases
buildDeps='curl gnupg'
set -x
apt-get update && apt-get install -y $buildDeps
gpg-agent --daemon
# Use an alternative key server to improve reliability of CI
gpg --keyserver pgp.mit.edu --recv-keys $GOSU_DOWNLOAD_KEY || \
  gpg --keyserver hkp://hkps.pool.sks-keyservers.net:80 --recv-keys $GOSU_DOWNLOAD_KEY
echo "trusted-key $GOSU_DOWNLOAD_KEY" >> /root/.gnupg/gpg.conf
curl -sSL "$GOSU_DOWNLOAD_URL" > gosu-amd64
curl -sSL "$GOSU_DOWNLOAD_SIG" > gosu-amd64.asc
gpg --verify gosu-amd64.asc
rm -f gosu-amd64.asc
mv gosu-amd64 /usr/bin/gosu
chmod +x /usr/bin/gosu

# Install nginx to be able to serve content from this container
apt-get install -y nginx
rm -rf /etc/nginx/*.d
mkdir -p /etc/nginx/addon.d /etc/nginx/conf.d /etc/nginx/host.d /etc/nginx/nginx.d
usermod -a -G www-data compute

# Clean
apt-get autoremove --purge --allow-remove-essential -y $buildDeps && rm -rf /var/lib/apt/lists/*
rm -rf /root/.gnupg

# Create our standard directories:
#   /data/in : input data should go there
#   /data/out : output data should go there
#   /var/www/html : root for the HTML documentation which can be served when the container is executed in serve mode
mkdir -p /data/in /data/out /var/www/html/ /src
chown -R compute:compute /data/in /data/out /src
chown -R root:www-data /var/www/html/
