set -e

# first argument jar location
JAR=$1

# main paths
LATER_DIR=.layer
LAYER_TAR=packtar/layers/layer.tar
LAYER_GZIP=packtar/layers/layer.tar.gzip
LAYER_JSON=packtar/layers/layer.json

# create the fusion root path
# and give create permissions to all
mkdir -p .layer/fusion/s3
chmod 777 .layer/fusion/s3

# make the layer tar
rm -f $LAYER_TAR*
java -cp $JAR io.seqera.util.LayerAssemblerCli .layer pack