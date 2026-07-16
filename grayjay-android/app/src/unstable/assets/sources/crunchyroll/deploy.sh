#!/bin/sh
DOCUMENT_ROOT=/var/www/sources

# Use environment variable to determine deployment type
PRE_RELEASE=${PRE_RELEASE:-false} # Default to false if not set

# Determine deployment directory
if [ "$PRE_RELEASE" = "true" ]; then
    RELATIVE_PATH="pre-release/Crunchyroll"
else
    RELATIVE_PATH="Crunchyroll"
fi

DEPLOY_DIR="$DOCUMENT_ROOT/$RELATIVE_PATH"
PLUGIN_URL_ROOT="https://plugins.grayjay.app/$RELATIVE_PATH"
SOURCE_URL="$PLUGIN_URL_ROOT/CrunchyrollConfig.json"

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
mkdir -p "$DEPLOY_DIR"
cp CrunchyrollIcon.png "$DEPLOY_DIR"
cp CrunchyrollConfig.json "$DEPLOY_DIR"
cp CrunchyrollScript.js "$DEPLOY_DIR"

# Update the sourceUrl in the config file only if it's a pre-release
if [ "$PRE_RELEASE" = "true" ]; then
    # Update the sourceUrl in CrunchyrollConfig.json
    echo "Updating sourceUrl in CrunchyrollConfig.json..."
    jq --arg sourceUrl "$SOURCE_URL" '.sourceUrl = $sourceUrl' "$DEPLOY_DIR/CrunchyrollConfig.json" >"$DEPLOY_DIR/CrunchyrollConfig_temp.json"
    if [ $? -eq 0 ]; then
        mv "$DEPLOY_DIR/CrunchyrollConfig_temp.json" "$DEPLOY_DIR/CrunchyrollConfig.json"
    else
        echo "Failed to update CrunchyrollConfig.json" >&2
        exit 1
    fi
    echo "Updated sourceUrl in config file for pre-release."
else
    echo "Skipping sourceUrl update in config file (not a pre-release)."
fi

sh sign.sh "$DEPLOY_DIR/CrunchyrollScript.js" "$DEPLOY_DIR/CrunchyrollConfig.json"

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
    -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
    -H "Content-Type: application/json" \
    --data '{"files":["'"$PLUGIN_URL_ROOT/CrunchyrollIcon.png"'", "'"$PLUGIN_URL_ROOT/CrunchyrollConfig.json"'", "'"$PLUGIN_URL_ROOT/CrunchyrollScript.js"'"]}'

# Take site back online
echo "Bringing site back online..."
rm "$DOCUMENT_ROOT/maintenance.file"
