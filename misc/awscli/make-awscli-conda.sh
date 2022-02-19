rm -rf $PWD/.layer/opt/conda 

docker run \
 --platform linux/amd64 \
 --rm \
 -v $PWD/.layer/opt/conda:/opt/conda \
 ubuntu \
 sh -c "apt-get update && apt-get install -y bzip2 wget; \
   wget https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh; \
   bash Miniconda3-latest-Linux-x86_64.sh -b -f -p /opt/conda; \
   /opt/conda/bin/conda install -c conda-forge -y awscli"
