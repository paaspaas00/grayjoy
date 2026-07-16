#!/bin/bash

# Exit on any error
set -e

echo "Updating country options in RadioBrowserConfig.json"

# Ensure that required commands are available
command -v curl >/dev/null 2>&1 || { echo "Error: curl is not installed." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "Error: jq is not installed." >&2; exit 1; }

# Fetch countries data sorted alphabetically by name
echo "Fetching countries from Radio Browser API..."
countries_json=$(curl --silent --request GET --url "https://all.api.radio-browser.info/json/countries?order=name")

# Process countries data into options array
# Format: "iso_code - name" (e.g., "US - The United States Of America")
# API returns countries sorted alphabetically by name
# Also normalize ISO codes to uppercase to avoid duplicates (like US/us)
echo "Processing country data..."
options_array=$(echo "$countries_json" | jq -r '
  # Group by uppercase ISO code and sum station counts
  group_by(.iso_3166_1 | ascii_upcase) | 
  map({
    iso_3166_1: .[0].iso_3166_1 | ascii_upcase,
    name: .[0].name,
    stationcount: map(.stationcount) | add
  }) |
  # Re-sort by name to maintain API ordering after grouping
  sort_by(.name) |
  # Filter countries with stations
  map(select(.stationcount > 0)) | 
  map("\(.iso_3166_1) - \(.name)") | 
  # Extract US and remove it from the list
  . as $all |
  ($all | map(select(startswith("US - "))) | .[0]) as $us |
  ($all | map(select(startswith("US - ") | not))) as $rest |
  # Build final list with all, US, then rest
  ["all - Global (All Countries)"] + (if $us then [$us] else [] end) + $rest
')

# Update the existing config file
echo "Updating RadioBrowserConfig.json..."
jq --argjson options "$options_array" '
  # Find the index of the homeCountry setting if it exists
  (.settings | map(.variable == "homeCountry") | index(true)) as $idx |
  
  # Create the new setting object
  {
    "variable": "homeCountry",
    "name": "Home Country",
    "description": "Choose which country to show on the home page.",
    "type": "Dropdown",
    "default": "1",
    "options": $options
  } as $new_setting |
  
  # If the setting exists, update it; otherwise add it
  if $idx != null then
    .settings[$idx] = $new_setting
  else
    .settings += [$new_setting]
  end
' RadioBrowserConfig.json > RadioBrowserConfig.tmp.json

# Move the temporary file to the original
mv RadioBrowserConfig.tmp.json RadioBrowserConfig.json

# Calculate how many countries are in the list
country_count=$(echo "$options_array" | jq 'length - 1')  # -1 for the "all" option

echo "Successfully updated RadioBrowserConfig.json with $country_count countries!"
echo "Use this script to update the country list when needed."