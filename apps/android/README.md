# Moonlight Android

> This folder is the Android client of Borealis. It started as a subtree of
> [moonlight-android](https://github.com/moonlight-stream/moonlight-android) 12.2 and has been Borealis code since
> [ADR 0054](../../docs/adr/0054-declare-independence-from-moonlight.md). The text below is the upstream introduction, kept
> for the credit to Moonlight until Sprint 19 rewrites this file. The Borealis modules and the build logic are described in
> [borealis/README.md](borealis/README.md).

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow the development of Moonlight on its [Discord server](https://moonlight-stream.org/discord).

## Building

The tools, the Android SDK and NDK setup and the build and check commands are in
[docs/development/building.md](../../docs/development/building.md). The NDK version is pinned in `app/build.gradle`.

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
