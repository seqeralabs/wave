#!/bin/sh
set -e
## enable debug mode
[ $WAVE_DEBUG ] && set -x
if [ "$NXF_FUSION_BUCKETS" ]; then
  (IFS=',';
  for x in $NXF_FUSION_BUCKETS; do
    path=$(echo $x | sed s@'s3://'@@)
    mkdir -p /fusion/s3/$path
    ## replace the first slash with a `:` character
    ## because this is the format expected by geesefs
    bucket=$(echo $path | sed 's#/#:#')
    /opt/geesefs/geesefs --endpoint https://s3.amazonaws.com --file-mode=0755 $bucket /fusion/s3/$path
  done)
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
if [ "$WAVE_ENTRY_CHAIN" ]; then
  exec $(tini) -- "$WAVE_ENTRY_CHAIN" "$@"
else
  exec $(tini) -- "$@"
fi
