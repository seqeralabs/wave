# AWS-CLI layer 

It should be explored how create a layer that allows the execution 
of the `aws` CLI tool in a portable manner. 

Main choices: 
* Conda installation: https://www.nextflow.io/docs/latest/awscloud.html#aws-cli-installation
* Mamba installation: https://github.com/mamba-org/mamba#micromamba
* AWS CLI 2: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html 
                                                                                                     

### Portability 

The main problem is that both installation above dynamic linking with glibc library. 
This causes portability problem, especially on Alpine-based containers. See [musl vs glibc](https://stackoverflow.com/questions/33382707/alpine-musl-vs-glibc-are-they-supposed-to-be-compatible).

Apparently the AWS cli team is working on a solution see: 


* https://github.com/kyleknap/aws-cli/blob/source-proposal/proposals/source-install.md
* https://github.com/aws/aws-cli/pull/6352#issuecomment-909083091


### examples 

    docker run --rm \
    --platform linux/amd64 \
    -e AWS_REGION=eu-west-1 \
    -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
    -v $PWD/.layer/opt/conda:/opt/conda \
    busybox \
    /opt/conda/bin/aws 
