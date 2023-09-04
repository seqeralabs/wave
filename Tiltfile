expected_ref = "$EXPECTED_REF"

custom_build(
  ref='localhost:5005/wave',
  command = './gradlew jibDockerBuild --image=' + expected_ref + ' && docker push ' + expected_ref,
  deps=['src'])





