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
