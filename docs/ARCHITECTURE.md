# Flowesal Android — rootless architecture

## Goal

Provide a stock-Android implementation that never asks for root, Magisk, KernelSU, Shizuku, iptables or NFQUEUE.

## Runtime path

Android `VpnService` → local TUN → userspace packet/flow engine → `VpnService.protect()` for upstream sockets → Internet.

Android's `VpnService` is the supported system API for applications that create a virtual network interface. The VPN permission is granted by the user through the system UI.

## Current implementation status

This repository is a **buildable foundation**, not yet a drop-in replacement for Linux `nfqws`/Flowseal. The current service implements a narrow DNS interception/forwarding path so the project can be built and the VPN permission/TUN lifecycle can be tested safely.

The next engine milestone is:

1. IPv4 packet parser/checksum layer.
2. TCP flow termination in userspace.
3. Protected upstream sockets.
4. TLS ClientHello/SNI parsing.
5. HTTP Host parsing.
6. Desync strategy module (fake/multisplit/hostfakesplit).
7. UDP/QUIC handling.
8. Per-domain allowlist and automatic profile selection.

Do not advertise the current build as a complete DPI bypass until these stages are implemented and tested on a physical device.
