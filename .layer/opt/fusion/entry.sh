#!/bin/sh
set -e
## enable debug mode
[ $XREG_DEBUG ] && set -x
if [ "$NXF_FUSION_BUCKETS" ]; then
  mkdir -p /fusion/s3
  /opt/fusion/fusionfs &
  i=0; until (cat /proc/mounts | grep /fusion/s3) || [ $((++i)) -gt 30 ]; do sleep 1; done >/dev/null \
    || { echo "ERROR mounting FusionFS at /fusion/s3"; exit 1; }
fi
## make sure to shutdown the fuse driver
on_exit() {
  if pgrep fusionfs >/dev/null; then
  { >&2 echo "$(date '+%Y-%m-%d_%H:%M:%S') Shutdown fusionfs"
    kill $(pgrep fusionfs)
    >&2 echo "$(date '+%Y-%m-%d_%H:%M:%S') Done"
  }>&2
  fi
}
trap on_exit EXIT
## invoke the target command
>&2 echo "$(date '+%Y-%m-%d_%H:%M:%S') Begin"
if [ "$XREG_ENTRY_CHAIN" ]; then
  "$XREG_ENTRY_CHAIN" "$@"
else
  "$@"
fi
