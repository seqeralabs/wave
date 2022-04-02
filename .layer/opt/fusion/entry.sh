#!/bin/sh
set -e
## enable debug mode
[ $XREG_DEBUG ] && set -x
if [ "$NXF_FUSION_BUCKETS" ]; then
  uid=$(id -u)
  gid=$(id -g)
  (IFS=',';
  for x in $NXF_FUSION_BUCKETS; do
    path=$(echo $x | sed s@'s3://'@@)
    mkdir -p /fusion/s3/$path
    ## replace the first slash with a `:` character
    ## because this is the format expected by goofys
    bucket=$(echo $path | sed 's#/#:#')
    /opt/goofys/goofys --file-mode=0755 --uid $uid --gid $gid $bucket /fusion/s3/$path
  done)
fi
## mount juicefs
if [ "$XREG_JUICE_URL" ]; then
  mkdir -p "$XREG_JUICE_PATH"
  juicefs mount "$XREG_JUICE_URL" "$XREG_JUICE_PATH" -d
fi
## make sure to shutdown the fuse driver
on_exit() {
  if pgrep goofys >/dev/null; then
  { >&2 echo "$(date '+%Y-%m-%d_%H:%M:%S') Shutdown goofys"
    kill $(pgrep goofys)
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
