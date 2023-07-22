mv wave.log wave.log.bak
export AWS_REGION=${AWS_REGION:-'eu-west-1'}
./gradlew run --continuous --watch-fs
