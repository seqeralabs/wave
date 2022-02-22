set -e

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
tar --preserve-permissions --owner=0 --group=0 -vcf $LAYER_TAR -C $LATER_DIR $(ls $LATER_DIR)
CHECKSUM_TAR=$(< $LAYER_TAR sha256sum | awk '{ print $1 }')

# compress the TAR file, not `-n` to prevent adding timestamp
# in the header metadata which will cause the inconsistent hashing
gzip -n --suffix .gzip $LAYER_TAR
CHECKSUM_GZIP=$(< $LAYER_GZIP sha256sum | awk '{ print $1 }')

# create the json metadata 
JSON_STRING=$( jq -n \
                  --arg location "$LAYER_GZIP" \
                  --arg tar_sha256 "sha256:$CHECKSUM_TAR" \
                  --arg gzip_sha256 "sha256:$CHECKSUM_GZIP" \
                  '{location: $location, gzipDigest: $gzip_sha256, tarDigest: $tar_sha256}' )

cat config.json | jq ".append += $JSON_STRING" > $LAYER_JSON
