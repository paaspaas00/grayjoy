#!/bin/sh
DOCUMENT_ROOT=/var/www/sources

# Use environment variable to determine deployment type
PRE_RELEASE=${PRE_RELEASE:-false}  # Default to false if not set

# Determine deployment directory
if [ "$PRE_RELEASE" = "true" ]; then
    RELATIVE_PATH="pre-release/RadioBrowser"
else
    RELATIVE_PATH="RadioBrowser"
fi

DEPLOY_DIR="$DOCUMENT_ROOT/$RELATIVE_PATH"
PLUGIN_URL_ROOT="https://plugins.grayjay.app/$RELATIVE_PATH"
SOURCE_URL="$PLUGIN_URL_ROOT/RadioBrowserConfig.json"

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
mkdir -p "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/media"
cp RadioBrowserIcon.png "$DEPLOY_DIR"
cp RadioBrowserConfig.json "$DEPLOY_DIR"
cp RadioBrowserScript.js "$DEPLOY_DIR"

# Copy all media files to the deployment directory
cp -r media/* "$DEPLOY_DIR/media/"
# Update the sourceUrl in RadioBrowserConfig.json
echo "Updating sourceUrl in RadioBrowserConfig.json..."
jq --arg sourceUrl "$SOURCE_URL" '.sourceUrl = $sourceUrl' "$DEPLOY_DIR/RadioBrowserConfig.json" > "$DEPLOY_DIR/RadioBrowserConfig_temp.json"
if [ $? -eq 0 ]; then
    mv "$DEPLOY_DIR/RadioBrowserConfig_temp.json" "$DEPLOY_DIR/RadioBrowserConfig.json"
else
    echo "Failed to update RadioBrowserConfig.json" >&2
    exit 1
fi

sh sign.sh "$DEPLOY_DIR/RadioBrowserScript.js" "$DEPLOY_DIR/RadioBrowserConfig.json"

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
     -H "Content-Type: application/json" \
     --data '{"files":["'"$PLUGIN_URL_ROOT/RadioBrowserIcon.png"'", "'"$PLUGIN_URL_ROOT/RadioBrowserConfig.json"'", "'"$PLUGIN_URL_ROOT/RadioBrowserScript.js"'", "'"$PLUGIN_URL_ROOT/media/DefaultThumbnail.png"'"]}'

# Take site back online
echo "Bringing site back online..."
rm "$DOCUMENT_ROOT/maintenance.file"
