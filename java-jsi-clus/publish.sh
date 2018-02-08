#!/usr/bin/env bash

set -o pipefail  # trace ERR through pipes
set -o errtrace  # trace ERR through 'time command' and other functions
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value

get_script_dir () {
     SOURCE="${BASH_SOURCE[0]}"

     while [ -h "$SOURCE" ]; do
          DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
          SOURCE="$( readlink "$SOURCE" )"
          [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
     done
     cd -P "$( dirname "$SOURCE" )"
     pwd
}

WORKSPACE=$(get_script_dir)

if pgrep -lf sshuttle > /dev/null ; then
  echo "sshuttle detected. Please close this program as it messes with networking and prevents builds inside Docker to work"
  exit 1
fi

if [ $NO_SUDO ]; then
  CAPTAIN="captain"
  DOCKER="docker"
elif groups $USER | grep &>/dev/null '\bdocker\b'; then
  CAPTAIN="captain"
  DOCKER="docker"
else
  CAPTAIN="sudo captain"
  DOCKER="sudo docker"
fi

# Build
echo "Build the project..."
./build.sh
# Cannot run the integration test now as the jar needs to be deployed to BinTray first
# ./tests/test.sh
echo "[ok] Done"

count=$(git status --porcelain | wc -l)
if test $count -gt 0; then
  git status
  echo "Not all files have been committed in Git. Release aborted"
  exit 1
fi

select_part() {
  local choice=$1
  case "$choice" in
      "Patch release")
          bumpversion patch
          ;;
      "Minor release")
          bumpversion minor
          ;;
      "Major release")
          bumpversion major
          ;;
      *)
          read -p "Version > " version
          bumpversion --new-version=$version all
          ;;
  esac
}

git pull --tags
# Look for a version tag in Git. If not found, ask the user to provide one
[ $(git tag --points-at HEAD | grep java-rapidminer | wc -l) == 1 ] || (
  latest_version=$(bumpversion --dry-run --list patch | grep current_version | sed -r s,"^.*=",, || echo '0.0.1')
  echo
  echo "Current commit has not been tagged with a version. Latest known version is $latest_version."
  echo
  echo 'What do you want to release?'
  PS3='Select the version increment> '
  options=("Patch release" "Minor release" "Major release" "Release with a custom version")
  select choice in "${options[@]}";
  do
    select_part "$choice"
    break
  done
  updated_version=$(bumpversion --dry-run --list patch | grep current_version | sed -r s,"^.*=",,)
  read -p "Release version $updated_version? [y/N] > " ok
  if [ "$ok" != "y" ]; then
    echo "Release aborted"
    exit 1
  fi
)

updated_version=$(bumpversion --dry-run --list patch | grep current_version | sed -r s,"^.*=",,)

# Build again to update the version
echo "Build the project for distribution..."
./build.sh
# Extract the jar from the Docker image and publish it to BinTray first to be able to execute the tests
mkdir -p target/
$DOCKER rm -f java-rapidminer-published 2> /dev/null || true
$DOCKER run -d --rm --name java-rapidminer-published hbpmip/java-rapidminer:latest serve
$DOCKER container cp java-rapidminer-published:/usr/share/jars/mip-rapidminer.jar target/mip-rapidminer-for-deploy.jar
$DOCKER rm -f java-rapidminer-published

mvn deploy:deploy-file \
  "-Durl=https://api.bintray.com/maven/hbpmedical/maven/eu.humanbrainproject.mip.algorithms:rapidminer/;publish=1" \
   -DrepositoryId=bintray-hbpmedical-maven -Dfile=target/mip-rapidminer-for-deploy.jar -DpomFile=pom.xml

./tests/test.sh
echo "[ok] Done"

git push
git push --tags

# Push on Docker Hub
#  WARNING: Requires captain 1.1.0 to push user tags
BUILD_DATE=$(date -Iseconds) \
  VCS_REF=$updated_version \
  VERSION=$updated_version \
  WORKSPACE=$WORKSPACE \
  $CAPTAIN push target_image --branch-tags=false --commit-tags=false --tag $updated_version

# Notify Microbadger
curl -XPOST https://hooks.microbadger.com/images/hbpmip/java-rapidminer/eqm5EMJzbfgo1X3c_E03j5YxL1c=

# Notify on slack
sed "s/USER/${USER^}/" $WORKSPACE/slack.json > $WORKSPACE/.slack.json
sed -i.bak "s/VERSION/$updated_version/" $WORKSPACE/.slack.json
curl -k -X POST --data-urlencode payload@$WORKSPACE/.slack.json https://hbps1.chuv.ch/slack/dev-activity
rm -f $WORKSPACE/.slack.json $WORKSPACE/.slack.json.bak
