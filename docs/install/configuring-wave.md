
## Email Configuration.

### SMTP 

Wave can be configured to send notification emails this requires an SMTP Server

Add `mail` to your micronaut environment and configure mail as such

```yaml
mail:
  from: REPLACE_ME_WAVE_CONTACT_EMAIL
  smtp:
    host: REPLACE_ME_TOWER_SMTP_HOST
    port: "REPLACE_ME_TOWER_SMTP_PORT"
    user: REPLACE_ME_TOWER_SMTP_USER                    # (!) Security Enhancement Candidate.
    password: REPLACE_ME_TOWER_SMTP_PASSWORD            # (!) Security Enhancement Candidate.
    auth: true
    starttls:
      enable: true
      required: true
    ssl:
      protocols: "TLSv1.2"
```

### SES 

Note if you're intending to use AWS SES & IAM Authentication you must add the micronaut environment `aws-ses` along with `mail`

Please note that Wave's SES integration requires SES to be configured in the same region as the Wave deployment and will use the configured IAM role to 

```yaml
mail:
  from: REPLACE_ME_WAVE_CONTACT_EMAIL
```

## Configuring Logging

## Configuring Build logs

## Platform Integration

## Enabling Scanning

Wave can scan builds please note this requires builds to be enabled set the config value of `wave.build.scan` to `true`

```
  config.yml:
    wave:
      build:
        scan: false
```

## ECR Caching

Wave can be configured to store intermediate layers to ECR to improve build times. 

This can be done by configuring 

```yaml

```
