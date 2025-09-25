# Infineon Radar Service

This is the place for common code, algorithms, etc.

Think of it as a "smart" entity, as opposed to the "dumb" HAL, which only starts/stops data acquisition and gives raw data.

We are using LiteRT (ex TensorFlow Lite) so that we can deploy AI models, e.g., for classification.
Currently we only generate range doppler maps to display them in the applications, for demonstration purpose.

You can find details on how [`assets/range_doppler.tflite`](./assets/range_doppler.tflite) has been generated in the [`model/`](./model) directory.

## How to build

Refer to https://github.com/Paradox-Cat-GmbH/aosp.local-manifests.paradoxcat-infineon-radar

## How to run development version

To quickly update the service on device:

```bash
m InfineonRadarService
adb root
adb remount system
adb sync system
```

Since it is a bound service, you may need to restart the application which binds to it.

## How to access InfineonRadarService from applications

There are multiple ways to access the InfineonRadarService.

1. Distribute the AIDL and the service name.
  - Application will have to write the binder boilerplate and manage the connection manually.
  - App will be depending on AIDL
2. Use InfineonRadarManager.
   It is an established Android pattern to provide a helper class, which manages the binder connection for you and provides interfaces which are simple to use, so we also provide one.
   Here there are 2 options:
   1. Build an InfineonRadarManager.jar using `m InfineonRadarManager`. 
     - Find the stub, e.g.,
       `out/soong/.intermediates/packages/services/paradoxcat/infineon-radar-service/manager/InfineonRadarManager.stubs/android_common/turbine-combined/InfineonRadarManager.stubs.jar`
       Note: if the stub jar is missing, you may need to remove `system_ext_specific: true` from the `manager/Android.bp`.
     - Put the stub into your `app/libs/InfineonRadarManager.jar`
     - Add `compileOnly(files("libs/InfineonRadarManager.jar"))` to `build.gradle` of the app
   2. Build a whole `sdk_addon`.
     - For that you would need to build an emulator, `m sdk_addon` does not seem work on hardware targets.
     - TODO: add more instructions, it's quite complex.

