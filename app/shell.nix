{ pkgs ? import <nixpkgs> {} }:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "11.0";
    buildToolsVersions = [ "35.0.0" ];
    platformVersions = [ "35" ];
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
    includeNDK = false;
  };
  androidSdk = androidComposition.androidsdk;
in
pkgs.mkShell {
  buildInputs = [
    pkgs.jdk21
    pkgs.ktfmt
    pkgs.patchelf
    androidSdk
  ];

  ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
  JAVA_HOME = "${pkgs.jdk21}";
  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
    pkgs.stdenv.cc.cc.lib
    pkgs.zlib
  ];

  shellHook = ''
    # Patch dynamically linked Android SDK binaries for NixOS
    for f in $(find ~/.gradle/caches -name "aapt2" -type f 2>/dev/null); do
      if file "$f" | grep -q "ELF" && ! patchelf --print-interpreter "$f" 2>/dev/null | grep -q "/nix/store"; then
        echo "Patching $f for NixOS..."
        patchelf --set-interpreter "$(cat ${pkgs.stdenv.cc}/nix-support/dynamic-linker)" "$f"
      fi
    done

    echo "Android development environment loaded"
    echo "JAVA_HOME: $JAVA_HOME"
    echo "ANDROID_HOME: $ANDROID_HOME"
    echo ""
    echo "Java version:"
    java -version
  '';
}
