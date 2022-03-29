export XREG_ARCH=$(uname -m)
export MICRONAUT_CONFIG_FILES="classpath:application.yml,file:config.yml"
./gradlew run
