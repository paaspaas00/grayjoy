#!/bin/sh
DOCUMENT_ROOT=/var/www/sources

# Use environment variable to determine deployment type
PRE_RELEASE=${PRE_RELEASE:-false}  # Default to false if not set

# Determine deployment directory
if [ "$PRE_RELEASE" = "true" ]; then
    RELATIVE_PATH="pre-release/TedTalks"
else
    RELATIVE_PATH="TedTalks"
fi

DEPLOY_DIR="$DOCUMENT_ROOT/$RELATIVE_PATH"
PLUGIN_URL_ROOT="https://plugins.grayjay.app/$RELATIVE_PATH"
SOURCE_URL="$PLUGIN_URL_ROOT/TedTalksConfig.json"

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
mkdir -p "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/media"  # Create media directory if it doesn't exist
cp TedTalksIcon.png "$DEPLOY_DIR"
cp TedTalksConfig.json "$DEPLOY_DIR"
cp TedTalksScript.js "$DEPLOY_DIR"
cp media/speaker.png "$DEPLOY_DIR/media/"  # Copy the speaker.png file to media folder
# Update the sourceUrl in TedTalksConfig.json
echo "Updating sourceUrl in TedTalksConfig.json..."
jq --arg sourceUrl "$SOURCE_URL" '.sourceUrl = $sourceUrl' "$DEPLOY_DIR/TedTalksConfig.json" > "$DEPLOY_DIR/TedTalksConfig_temp.json"
if [ $? -eq 0 ]; then
    mv "$DEPLOY_DIR/TedTalksConfig_temp.json" "$DEPLOY_DIR/TedTalksConfig.json"
else
    echo "Failed to update TedTalksConfig.json" >&2
    exit 1
fi

sh sign.sh "$DEPLOY_DIR/TedTalksScript.js" "$DEPLOY_DIR/TedTalksConfig.json"

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
     -H "Content-Type: application/json" \
     --data '{"files":["'"$PLUGIN_URL_ROOT/TedTalksIcon.png"'", "'"$PLUGIN_URL_ROOT/TedTalksConfig.json"'", "'"$PLUGIN_URL_ROOT/TedTalksScript.js"'", "'"$PLUGIN_URL_ROOT/media/speaker.png"'"]}'

# Take site back online
echo "Bringing site back online..."
rm "$DOCUMENT_ROOT/maintenance.file"
