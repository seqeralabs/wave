set -e

# mac deps 
[ $(uname) = Darwin ] && TAR=gtar || TAR=tar

# main paths
LATER_DIR=.layer
LAYER_TAR=pack/layers/layer.tar
LAYER_GZIP=pack/layers/layer.tar.gzip
LAYER_JSON=pack/layers/layer.json

# create the fusion root path
# and give create permissions to all
mkdir -p .layer/fusion/s3
chmod 777 .layer/fusion/s3

# make the layer tar
rm -f $LAYER_TAR*
mkdir -p $(dirname $LAYER_TAR)
$TAR --preserve-permissions --owner=0 --group=0 -vcf $LAYER_TAR -C $LATER_DIR $(ls $LATER_DIR)
CHECKSUM_TAR=$(< $LAYER_TAR sha256sum | awk '{ print $1 }')

# compress the TAR file, with `-n` to prevent adding timestamp
# in the header metadata which will cause the inconsistent hashing
gzip -k -n --suffix .gzip $LAYER_TAR
CHECKSUM_GZIP=$(< $LAYER_GZIP sha256sum | awk '{ print $1 }')
SIZE_GZIP=$(wc -c "$LAYER_GZIP" | awk '{print $1}')

# create the json metadata 
JSON_STRING=$( jq -n \
                  --arg location "$(basename $LAYER_GZIP)" \
                  --arg tar_sha256 "sha256:$CHECKSUM_TAR" \
                  --arg gzip_sha256 "sha256:$CHECKSUM_GZIP" \
                  --arg gzip_size "$SIZE_GZIP" \
                  '{location: $location, gzipDigest: $gzip_sha256, gzipSize: $gzip_size|tonumber, tarDigest: $tar_sha256}' )

cat config.json | jq ".append += $JSON_STRING" > $LAYER_JSON
