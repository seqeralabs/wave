#!/bin/sh
set -e
## enable debug mode
[ $XREG_DEBUG ] && set -x
## holds the list of buckets to be mounted
if [ "$NXF_FUSION_BUCKETS" ]; then
  #export AWS_MAX_ATTEMPTS={{aws_max_attempts}}
  mkdir -p /fusion/s3
  /opt/fusion/fusionfs &
  timeout 10s sh -c 'until (cat /proc/mounts | grep "/fusion/s3"); do sleep 1; done >/dev/null' || { echo "ERROR mounting FusionFS at /fusion/s3"; exit 1; }
fi
## invoke the target command
if [ "$XREG_ENTRY_CHAIN" ]; then
  exec "$XREG_ENTRY_CHAIN" "$@"
else
  exec "$@"
fi
