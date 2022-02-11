#!/bin/sh
set -e
## enable debug mode
[ $XREG_DEBUG ] && set -x
## holds the list of buckets to be mounted
if [ "$NXF_FUSION_BUCKETS" ]; then
  uid=$(id -u)
  gid=$(id -g)
  #export AWS_MAX_ATTEMPTS={{aws_max_attempts}}
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
## invoke the target command
if [ "$XREG_ENTRY_CHAIN" ]; then
  exec "$XREG_ENTRY_CHAIN" "$@"
else
  exec "$@"
fi
