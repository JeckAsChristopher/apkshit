Place these two files here before building in Termux:

  liblocai_jni.so      — built by running:  ./build_native.sh /path/to/ndk
  libc++_shared.so     — copied by the script from NDK automatically

build_native.sh is in the project root. Run it on a PC/Mac/Linux once.
After copying the .so files here, Termux only needs Java + Gradle to build.
