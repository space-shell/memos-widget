{
  description = "Memos Widget - Android quick-capture client for self-hosted Memos";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "35" ];
            buildToolsVersions = [ "35.0.0" ];
            includeEmulator = false;
            includeSystemImages = false;
            includeNDK = false;
          };
        in
        {
          default = pkgs.mkShell {
            name = "memos-widget";
            packages = [
              pkgs.jdk17
              pkgs.git
              androidComposition.androidsdk
            ];
            env = {
              JAVA_HOME = "${pkgs.jdk17.home}";
              ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
            };
          };
        });
    };
}
