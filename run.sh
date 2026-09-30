#
#  Wave, containers provisioning service
#  Copyright (c) 2023-2024, Seqera Labs
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
#
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

mv wave.log wave.log.bak
export AWS_REGION=${AWS_REGION:-'eu-west-1'}

# AWS SSO: Wave needs static keys for the ECR build registry, and an SSO session only has
# temporary ones. So push through the ECR dual-stack endpoint instead, which Wave treats as
# a plain registry, with the ECR login token of the session. Run `aws sso login` first and
# `AWS_PROFILE=<profile> ./run.sh`; the token lasts 12 hours, then run this script again.
if [ -n "$AWS_PROFILE" ] && [ -z "$AWS_ACCESS_KEY_ID" ]; then
  ECR_HOST=195996028523.dkr-ecr.eu-west-1.on.aws
  ECR_LOGIN_TOKEN=$(aws ecr get-login-password --region eu-west-1) || exit 1
  export ECR_LOGIN_TOKEN
  export WAVE_BUILD_REPO=$ECR_HOST/wave/build/dev
  export WAVE_BUILD_CACHE=$ECR_HOST/wave/build/cache
  # config.yml plus the dual-stack registry, the token stays in the environment
  export WAVE_CONFIG_FILE=$PWD/build/config-sso.yml
  mkdir -p build
  { sed '/^\.\.\.$/d' config.yml
    printf '    %s:\n      username: "AWS"\n      password: "${ECR_LOGIN_TOKEN:}"\n' $ECR_HOST
  } > $WAVE_CONFIG_FILE
fi

./gradlew run --continuous --watch-fs --console=plain
