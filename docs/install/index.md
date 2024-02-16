---
title: Self-hosted installation
---

This guide describes how to install the Wave service on your own infrastructure.

## Prerequisites

Ensure that you meet the following requirements for the Wave service.

- You have access to Seqera Platform
- You have an AWS account for your organization with sufficient privileges to create new AWS services
- You have an OpenID Connect (OIDC) service configured on AWS for your organization

If you are using the enterprise version of Seqera Platform, we recommend that you install the Wave service in the same EKS cluster where Seqera Platform is installed.

## Requirements

The Wave service requires the following AWS services:

- AWS Elastic Kubernetes Service (EKS) cluster
- AWS S3 bucket for logs
- AWS Elastic File System (EFS)
- AWS Application Load Balancer
- AWS Certificate Manager
- AWS Simple Email Service (SES)
- AWS Elastic Container Registry (ECR) service
- AWS Elasticache
- AWS Route53

Each AWS service must be in the same AWS region.

## Configuration settings for the Wave service

After completing the procedure to configure all the required AWS services, the following environment are consumed in the Kubernetes manifests for the Wave service:

* `WAVE_HOSTNAME`: The host name to use to access the Wave service e.g. `wave.your-company.com`. This should match the host name used when creating the HTTPS certificate by using AWS Certificate manager.
* `WAVE_CONTAINER_BUILD_REPO`: The ECR repository name used to host the containers built by Wave e.g. `<YOUR ACCOUNT>.dkr.ecr.<YOUR REGION>.amazonaws.com/wave/build`.
* `WAVE_CONTAINER_CACHE_REPO`: The ECR repository name used to cache the containers built by Wave e.g. `<YOUR ACCOUNT>.dkr.ecr.<YOUR REGION>.amazonaws.com/wave/cache`.
* `WAVE_LOGS_BUCKET`: The AWS S3 bucket used to store the Wave logs e.g. `wave-logs-prod`.
* `WAVE_REDIS_HOSTNAME`: The AWS Elasticache instance hostname and port e.g. `<YOUR ELASTICACHE INSTANCE>.cache.amazonaws.com:6379`.
* `WAVE_SENDER_EMAIL`: The email address that will be used by Wave to send email e.g. `wave-app@your-company.com`. Note: it must an email address validated in your AWS SES setup.
* `TOWER_API_URL`: The API URL of your Seqera Platform installation e.g. `<https://your-platform-hostname.com>/api`.
* `AWS_EFS_VOLUME_HANDLE`: The AWS EFS shared file system instance ID e.g. `fs-12345667890`
* `AWS_CERTIFICATE_ARN`: The arn of the AWS Certificate created during the environment preparation e.g. `arn:aws:acm:<YOUR REGION>:<YOUR ACCOUNT>:certificate/<YOUR CERTIFICATE ID>`
* `AWS_IAM_ROLE`: The arn of the AWS IAM role granting permissions to AWS resources to the Wave service.
* `SURREAL_DB_PASSWORD`: User defined password to be used for embedded Surreal DB deployed by Wave.
* `SEQERA_CR_USER`: The username to access the Seqera container registry to providing the images for installing Wave service
* `SEQERA_CR_PASSWORD`: The password to access the Seqera container registry to providing the images for installing Wave service

## Procedure

In the following steps, the installation procedure describes how to configure the required AWS service requirements and how to configure and apply the Kubernetes manifests for the Wave service.

### Configure AWS S3

Wave uses the S3 bucket to store logs and other artifacts.

- From the AWS console, [create a new S3 bucket][s3] with the following settings:
  - Specify a name for your bucket
  - Specify a region
  - Optional: Specify a retention policy for logs of at least a year

### Configure AWS EFS

Wave uses AWS EFS as scratch storage for container builds.

- From the AWS console, [create an EFS file system][efs] with the following settings:
  - Specify a name for the EFS file system, such as `wave-build`

### Configure AWS SES

Wave uses SES to send emails about container builds.

- From the AWS console, [configure SES][ses].
- Complete additional steps.
- Configure your inbound mail server to accept emails from the sender address that the Wave service uses as the `From` address.

### Configure AWS Certificate Manager

Wave uses Certificate Manager for TLS certificates used by AWS Application Load Balancer. The domain that you use can be a subdomain, such as `wave.example.com`.

1.  If you have not yet configured Certificate Manager, [Register a domain name][cm-domain].
2.  From the [AWS Certificate Manager console][cm-console], complete the procedure to [Request a public certificate using the console][cm-req].
3.  After the validation process is complete, copy the ARN value for this certificate.
4.  TBD

### Configure Amazon Route 53

TBD.

### Configure Amazon ECR

The Wave service uses two ECR registries for build artifacts and caching.

1.  Complete the steps in [Getting started with Amazon ECR using the AWS Management Console][ecr] and specify the following value:
    - **Repository name**: Specify a name, such as `wave-build`
2. Copy the URI for the registry that you just created in the previous step.
3. 
4. Repeat the above step for a second repository and specify a name such as `wave-cache`.

### Configure Amazon ElastiCache

1.  Complete the steps in [Getting started with Amazon ElastiCache for Redis][redis]
2.  After creating the Redis server, copy the host name.

### Configure an IAM role

[Create an IAM role][iam-role-create] so that the Wave service has secure access to the resources that it requires.

- Create a role
- Create a trust policy
- Need IAM role ARN
- Need OIDC

```json
{
  "Statement": [
    {
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:GetRepositoryPolicy",
        "ecr:DescribeRepositories",
        "ecr:ListImages",
        "ecr:DescribeImages",
        "ecr:BatchGetImage",
        "ecr:GetLifecyclePolicy",
        "ecr:GetLifecyclePolicyPreview",
        "ecr:ListTagsForResource",
        "ecr:DescribeImageScanFindings",
        "ecr:CompleteLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:aws:ecr:<YOUR REGION>:<YOUR ACCOUNT>:repository/wave/*"
      ]
    },
    {
      "Action": "ecr:GetAuthorizationToken",
      "Effect": "Allow",
      "Resource": "*"
    },
    {
      "Action": [
        "ssm:DescribeParameters"
      ],
      "Effect": "Allow",
      "Resource": "*"
    },
    {
      "Action": "s3:ListBucket",
      "Effect": "Allow",
      "Resource": [
        "arn:aws:s3:::<YOUR WAVE BUCKERT>"
      ]
    },
    {
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Effect": "Allow",
      "Resource": [
          "arn:aws:s3:::<YOUR WAVE BUCKET>/*"
      ]
    },
    {
      "Action": [
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:aws:ssm:<YOUR REGION>:<YOUR ACCOUNT>:parameter/config/wave-*",
        "arn:aws:ssm:<YOUR REGION>:<YOUR ACCOUNT>:parameter/config/application*"
      ]
    }
  ],
  "Version": "2012-10-17"
}
```

[s3]: https://docs.aws.amazon.com/AmazonS3/latest/userguide/create-bucket-overview.html
[efs]: https://docs.aws.amazon.com/efs/latest/ug/gs-step-two-create-efs-resources.html
[ses]: https://docs.aws.amazon.com/ses/latest/dg/setting-up.html
[cm-console]: https://us-east-2.console.aws.amazon.com/acm/home
[cm-domain]: https://docs.aws.amazon.com/acm/latest/userguide/setup-domain.html
[cm-req]: https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html#request-public-console
[ecr]: https://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-console.html
[redis]: https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/GettingStarted.html
[iam-role-create]: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-user.html#roles-creatingrole-user-console
