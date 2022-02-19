rm -rf $PWD/.layer/opt/aws-cli

docker run \
 --platform linux/amd64 \
 --rm \
 -v $PWD/.layer/opt:/opt \
 debian \
 sh -c 'apt-get update && apt-get install -y curl zip; curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && unzip awscliv2.zip && ./aws/install -i /opt/aws-cli '
