#!/bin/sh
DOCUMENT_ROOT=/var/www/sources

# Take site offline
echo "Taking site offline..."
touch $DOCUMENT_ROOT/maintenance.file

# Swap over the content
echo "Deploying content..."
mkdir -p $DOCUMENT_ROOT/Bilibili
cp build/BiliBiliIcon.png $DOCUMENT_ROOT/Bilibili
cp build/BiliBiliConfig.json $DOCUMENT_ROOT/Bilibili
cp build/BiliBiliScript.js $DOCUMENT_ROOT/Bilibili
sh sign.sh $DOCUMENT_ROOT/Bilibili/BiliBiliScript.js $DOCUMENT_ROOT/Bilibili/BiliBiliConfig.json

# Notify Cloudflare to wipe the CDN cache
echo "Purging Cloudflare cache for zone $CLOUDFLARE_ZONE_ID..."
curl -X POST "https://api.cloudflare.com/client/v4/zones/$CLOUDFLARE_ZONE_ID/purge_cache" \
     -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
     -H "Content-Type: application/json" \
     --data '{"files":["https://plugins.grayjay.app/Bilibili/BiliBiliIcon.png", "https://plugins.grayjay.app/Bilibili/BiliBiliConfig.json", "https://plugins.grayjay.app/Bilibili/BiliBiliScript.js"]}'

# Take site back online
echo "Bringing site back online..."
rm $DOCUMENT_ROOT/maintenance.file