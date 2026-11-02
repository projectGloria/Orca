# WebP runtime alignment fix

youtubedl-android FFmpeg 0.18.1 contains five WebP libraries with 4 KB ELF segment alignment. FFmpeg crashes at startup on the Android 15 16 KB emulator with these originals. Replacing them with the following Termux builds restores startup. `app:prepareFfmpeg` applies these replacements to a generated AAR without changing the Maven cache or other engine components.

These unmodified libraries come from Termux package `libwebp 1.6.0.0.0.1+really1.6.0`, downloaded 2026-11-02:

* [aarch64 package](https://packages.termux.dev/apt/termux-main/pool/main/libw/libwebp/libwebp_1.6.0.0.0.1%2Breally1.6.0_aarch64.deb), SHA-256 `8ba7fcfadd1a9957a7632c6407d6ef3e41d34dbd96a41769b245592ed4559045`.
* [x86_64 package](https://packages.termux.dev/apt/termux-main/pool/main/libw/libwebp/libwebp_1.6.0.0.0.1%2Breally1.6.0_x86_64.deb), SHA-256 `3274f3b6b3b765ed5cfb3d33cf309a2222de7766ad53f0eaefcbe89563f2f7a3`.

Files were extracted from each package's `data.tar` archive under `data/data/com.termux/files/usr/lib/`. They retain the same unversioned SONAMEs as the bundled libraries. Both architectures are audited by `scripts/check_native_alignment.py` after packaging.

libwebp is BSD licensed. [Upstream source at v1.6.0](https://github.com/webmproject/libwebp/tree/v1.6.0), [Termux packaging/build recipe](https://github.com/termux/termux-packages/tree/master/packages/libwebp). The full license and patent grant are included in app assets.
