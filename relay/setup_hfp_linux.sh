#!/usr/bin/env bash
# ==============================================================================
# Linux PipeWire / WirePlumber Bluetooth HFP Setup Script
# Configures this machine to act as a Bluetooth Hands-Free (HF) audio peer
# so Phone A can route 2-way cellular call audio directly through it.
# ==============================================================================

set -e

echo "=== PTA Forwarder: Bluetooth HFP Setup ==="

# Check for bluetoothctl
if ! command -v bluetoothctl &> /dev/null; then
    echo "[-] bluetoothctl is not installed. Please install bluez."
    exit 1
fi

# Check for PipeWire / WirePlumber
if command -v wpctl &> /dev/null; then
    echo "[+] WirePlumber detected."
    wpctl status
elif command -v pactl &> /dev/null; then
    echo "[+] PulseAudio / PipeWire-Pulse detected."
else
    echo "[-] Neither wpctl nor pactl found. Please ensure PipeWire or PulseAudio is installed."
fi

echo ""
echo "=== Steps to pair Phone A ==="
echo "1. Run: bluetoothctl"
echo "2. Inside bluetoothctl prompt, run:"
echo "     power on"
echo "     discoverable on"
echo "     pairable on"
echo "     agent on"
echo "     default-agent"
echo "3. On Phone A (Android): Open Settings -> Bluetooth -> Pair with this PC/server."
echo "4. In bluetoothctl prompt, trust and connect:"
echo "     trust <PHONE_MAC_ADDRESS>"
echo "5. Check audio profile using wpctl or pavucontrol:"
echo "     Make sure the profile is set to 'Headset Head Unit' (HFP/HSP)."
echo "6. Test audio flow by starting the relay daemon:"
echo "     cd relay && npm start"
echo ""
echo "[+] Setup instructions complete."
