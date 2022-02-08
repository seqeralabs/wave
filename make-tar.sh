set -e

LATER_DIR=$PWD/.layer
LAYER_TAR=$PWD/layer.tar
LAYER_GZIP=$PWD/layer.tar.gzip

rm -f $LAYER_TAR*
gtar --owner=0 --group=0 -vcf $LAYER_TAR -C $LATER_DIR $(ls $LATER_DIR)
CHECKSUM_TAR=$(< $LAYER_TAR sha256sum | awk '{ print $1 }')

# compress the TAR file, not `-n` to prevent adding timestamp
# in the header metadata which will cause the inconsistent hashing
gzip -n --suffix .gzip $LAYER_TAR
CHECKSUM_GZIP=$(< $LAYER_GZIP sha256sum | awk '{ print $1 }')

JSON_STRING=$( jq -n \
                  --arg location "$LAYER_GZIP" \
                  --arg tar_sha256 "sha256:$CHECKSUM_TAR" \
                  --arg gzip_sha256 "sha256:$CHECKSUM_GZIP" \
                  '{location: $location, gzipDigest: $gzip_sha256, tarDigest: $tar_sha256}' )

cat config.json | jq ".append += $JSON_STRING" > layer.json
