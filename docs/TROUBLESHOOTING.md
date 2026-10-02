# Troubleshooting

Start with the PC server's log. It records phones connecting and disconnecting, rejected tokens and failed commands (never the token itself):

```
%LOCALAPPDATA%\GestureLink\logs\gesturelink.log
```

Paste that into the Explorer address bar to open it. The same folder holds `config.json` (port and pairing token); the tray menu's **Open config folder** goes there too.

## The app can't connect

The app says "Couldn't reach the PC - check the IP and that the server is running."

Work down this list; each step rules out one cause.

1. **Is the server running?** Look for the GestureLink icon near the clock (it may be in the hidden-icons arrow). If it isn't there, start it (`python run.py` or `GestureLink.exe`).
2. **Can anything reach it?** On the PC, open `http://localhost:8765/health` in a browser. You should see `{"status":"ok", ...}`. If that fails, the server didn't start; check the log, and see "Port already in use" below.
3. **Can the phone reach it?** In the phone's browser, open `http://<pc-ip>:8765/health`. If the PC's own browser works but the phone's doesn't, it's the firewall or the network (next two sections).
4. **Is the address right?** The IP is on the pairing screen's QR code, and `ipconfig` on the PC shows it (the "IPv4 Address" of your Wi-Fi or Ethernet adapter). A PC's address can change after a reboot or router restart, so a saved address can go stale: scan the QR code again.
5. **Did you change the port?** If `config.json` has a port other than 8765, the address needs it: `192.168.1.5:9000`. Scanning the QR code fills it in for you.

### Windows Firewall is blocking it

The most common cause. The first time the server runs, Windows asks whether to let it accept connections. If you clicked Cancel, or allowed it on **public** networks only, the phone is blocked.

- Open **Settings > Privacy & security > Windows Security > Firewall & network protection > Allow an app through firewall**, find Python (or `GestureLink`), and tick **Private**.
- Or, from an **administrator** PowerShell, allow the port directly (use your port if it isn't 8765):

  ```
  New-NetFirewallRule -DisplayName "GestureLink" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Private
  ```

- Also check that your Wi-Fi is set to a **Private** network (Settings > Network & internet > Wi-Fi > your network > Network profile type). Windows applies much stricter rules to "Public" networks.

### The phone and PC aren't on the same network

- Both must be on the same Wi-Fi/router. Mobile data on the phone won't work, and neither will a guest network.
- Some routers have **AP / client isolation** (often on for guest networks, sometimes on the main one), which stops Wi-Fi devices from seeing each other. Turn it off in the router settings.
- A VPN on the PC or phone can hide the local network. Pause it and try again.
- The 5 GHz and 2.4 GHz bands of one router are normally the same network, but some routers separate them. Try putting both devices on the same band.

### The QR code has the wrong address

The server works out its address by asking Windows which adapter it would use to reach the internet. With a VPN running, or several network adapters (virtual machines, Docker, Hyper-V), it can pick the wrong one, or `127.0.0.1` if there's no route at all. Pause the VPN and restart the server, or type the correct IP by hand.

### Port already in use

If something else on the PC already uses port 8765, GestureLink shows an error saying it can't listen on that port and exits (the log has the exact bind error). Set a different `"port"` in `config.json` (in `%LOCALAPPDATA%\GestureLink`), start GestureLink again, and allow the new port through the firewall. If `http://localhost:8765/health` answers while GestureLink isn't running, the other program is the one using the port.

## The pairing token

When you tap Connect the app opens the connection and then checks the token with the PC. A **wrong token** (or one that was regenerated since you last paired) stays on the pairing screen with "That pairing token isn't right".

- Compare the token you entered with the one in the tray menu. Tokens are 8 characters (digits and the letters a-f) and easy to mistype: scanning the QR code avoids that.
- If you used **Regenerate pairing token** on the PC, every phone has to pair again with the new one.
- After 5 wrong tokens in a row from one phone, the server refuses everything from it for 30 seconds ("too many failed attempts, try again shortly"). Wait half a minute, fix the token, and try again.

## Scanning the QR code

- The app asks for **camera** permission the first time. If you refused it, enable it in Android Settings > Apps > GestureLink > Permissions.
- Hold the phone 20-30 cm from the screen and make sure the code is fully visible. If it won't scan, type the address and token instead.

## It connects, then drops

- The PC went to sleep or hibernated: the connection ends and the app returns to the pairing screen. Wake the PC, then tap Connect (the app reconnects automatically the next time it starts, not while it's running).
- **Turning the PC's Wi-Fi off from the phone cuts your own connection** if the PC is on Wi-Fi. Do that only when the PC is on Ethernet.
- Phones may close network connections of an app that has been in the background for a while. Reopen the app.

## Specific features

**Wake PC does nothing.** Wake-on-LAN needs the PC wired (Ethernet), the feature enabled in the BIOS/UEFI and in the network adapter's settings, and often Windows "Fast startup" turned off. The phone can only wake a PC whose MAC address it has learned, which happens automatically after one successful connection. See [pc-server/README.md](../pc-server/README.md) for the full setup.

**Mouse and keyboard don't work in some windows.** Windows doesn't let a normal program send input to a window running as administrator, or to the lock screen and UAC prompts. Start the server as administrator if you need that (right-click > Run as administrator).

**Screenshot is black or stale.** The screen usually can't be captured while the PC is locked, and some protected video (DRM) shows as black.

**Brightness card is missing, or setting it fails.** Brightness control only works on built-in laptop panels. Most external monitors don't expose it to Windows.

**File download fails.** Saving to the phone's Downloads folder needs Android 10 or newer. Files over 15 MB are refused in both directions.

**"... already exists in that folder" when renaming or creating a folder.** The server never overwrites on rename or create. Pick another name. (Uploading a file *does* replace one with the same name.)

**Delete asks a question on the PC.** Deleting moves things to the Recycle Bin. If Windows can't recycle an item (for example on a network drive), it asks on the PC's screen before deleting it permanently.

**End process says it refused.** The server won't end itself or critical Windows processes (csrss, winlogon, lsass, and similar). It also refuses if the process list is stale and the process ID now belongs to something else: refresh the list and try again.

**Clipboard says "clipboard is in use by another program".** Another app is holding the Windows clipboard. Try again in a moment.

## Still stuck?

Open an issue with the last lines of `gesturelink.log` (it's safe to share: it never contains the token), what the app showed, and your Windows and Android versions.
