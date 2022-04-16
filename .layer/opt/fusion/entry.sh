#!/bin/sh
set -e
## enable debug mode
[ $XREG_DEBUG ] && set -x
## mount juicefs
if [ "$XREG_JUICE_URL" ]; then
  mkdir -p /var/log
  mkdir -p "$XREG_JUICE_MOUNT"
  /opt/juicefs/juicefs mount "$XREG_JUICE_URL" "$XREG_JUICE_MOUNT" -d
fi

# tini wrapper to detect to detected the correct version to use 
# read more https://github.com/krallin/tini
tini() {
  if [ -f /etc/alpine-release ]; then
    echo /opt/tini/tini-static-muslc-amd64
  else
    echo /opt/tini/tini-static-amd64
  fi
}
## invoke the target command
if [ "$XREG_ENTRY_CHAIN" ]; then
  exec $(tini) -- "$XREG_ENTRY_CHAIN" "$@"
else
  exec $(tini) -- "$@"
fi
