#!/bin/bash

##########################################################################################
# ci/scripts/cf-push.sh backportbot "--var CLIENT_ID=$ID --var CLIENT_SECRET=$SECRET --var GITHUB_WEBHOOK_SECRET=$HOOK --var ISSUEMASTER_PERSONAL_ACCESS_TOKEN=$TOKEN --var INFO_VERSION=$VERSION"

APP_NAME=$1
# --var CLIENT_ID=$CLIENT_ID --var CLIENT_SECRET=$CLIENT_SECRET --var ISSUEMASTER_PERSONAL_ACCESS_TOKEN=$ISSUEMASTER_PERSONAL_ACCESS_TOKEN --var GITHUB_WEBHOOK_SECRET=$GITHUB_WEBHOOK_SECRET --var INFO_VERSION=$INFO_VERSION
PUSH_ARGS=$2
##########################################################################################

# the name that the current production application will get temporarily while we deploy
# the new app to APP_NAME
VENERABLE_APP_NAME="$APP_NAME-venerable"

cf delete $VENERABLE_APP_NAME -f
cf rename $APP_NAME $VENERABLE_APP_NAME

if cf push $APP_NAME -p build/libs/backport-bot-0.0.1-SNAPSHOT.jar $PUSH_ARGS ; then
  # the app started successfully so remove venerable app
  cf delete $VENERABLE_APP_NAME -f
else
  # the app failed to start so delete the newly deployed app and rename old app back
  cf delete $APP_NAME -f
  cf rename $VENERABLE_APP_NAME $APP_NAME
fi