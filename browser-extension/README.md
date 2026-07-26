# Grayjoy Link browser extension

Grayjoy Link transfers the active YouTube video or playlist from a Chromium browser to Grayjoy on
the same local network. It also exposes the computer playback state in Grayjoy and accepts
play/pause/previous/next commands from the phone notification.

## Install

1. Open `chrome://extensions` in Chrome, Edge, Brave, Vivaldi, or another Chromium browser.
2. Enable **Developer mode**.
3. Choose **Load unpacked** and select this `browser-extension` directory.
4. Pin **Grayjoy Link** to the toolbar.

## Pair

1. Connect the computer and phone to the same Wi-Fi/LAN.
2. Open the extension popup so its pairing QR is visible.
3. In Grayjoy open **Prefs → Paired computers → Scan pairing QR**.
4. After Grayjoy confirms the pairing, enter the Wi-Fi address shown on that same Grayjoy page
   and click **Connect**. The extension remembers it and can find nearby DHCP changes without
   sweeping whole private networks.
5. Allow Chrome's **Local network access** prompt. Discovery runs from the visible popup so
   current Chrome versions can request this permission before the background worker connects.
6. **I scanned it — connect nearby** tries the entered/saved address, nearby DHCP leases, and a
   bounded set of browser-visible/common LAN hints. It never scans complete `/24` networks.

The QR contains a random 256-bit secret. Every LAN request is authenticated with HMAC-SHA256,
a timestamp, and a single-use nonce. No cloud or relay server is used.

## Use

Open a YouTube video or playlist. Grayjoy shows an **On PC** card above the Home feed tabs.
**Play here** opens the current item on the phone at the desktop seek position. For playlists,
Grayjoy receives only the source playlist URL, current video URL, and seek position, then resolves
the playlist through its installed YouTube source.

The Android lock-screen notification controls the YouTube tab remotely. The extension must remain
installed and a YouTube tab must remain open.
