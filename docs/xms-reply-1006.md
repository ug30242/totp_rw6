# XMS reply failure 1006

Reported on Redmi Watch 6 China firmware 3.100.043: repeated phone hello packets
reach the receiver (`Phone data received (N)`), but native watch replies fail
with `1006` and Android times out before sending accounts. Vela documents 1006
as a disconnected interconnect channel. Receiving a packet proves only the
phone-to-watch direction; registering a message listener is not a round-trip check.

## Confirmed client defects and correction

The Android client bound to XMS, queried nodes and registered a message listener,
but never called Binder transaction 14, `registerServiceConnectedListener`.
The XMS 1.4 SDK calls this on each service connection. This registration supplies
a live client Binder separately from the per-node message listener.

The SDK's registration Binder also accepts transaction 1 with interface token
`com.xiaomi.xms.wearable.IServiceConnectedListener`, returning true without a
response payload. Its Java interface declares no methods, but its Stub handles
this acknowledgement. An automatically generated empty AIDL Stub rejects it.

`XmsClient` now retains this Binder, handles its acknowledgement and registers it
before node discovery or message listener setup. Registration exceptions abort
connection setup; they cannot lead to a ready state. The public MIT AIDL/types
remain unchanged. No proprietary SDK binary or replacement service is shipped.

## Contract evidence

- SDK sample: [zaona/xms-wearable-sdk at 297b9b0](https://github.com/zaona/xms-wearable-sdk/tree/297b9b0d22314f94009066caa13499ecdad8bc73),
  `app/libs/xms-wearable-lib_1.4_release.aar`.
- Independent identical AAR: [BandReader at 8aba961](https://github.com/zhaoyutiandc/BandReader_Android/tree/8aba961a7bcba0c5b7080e443dda780ec694f73d).
  AAR SHA-256: `9c40fd1c5409bb948523d503af71e2978ae522c35636afe7d64f474c0f6bc195`.
  Bytecode inspection only: API client service connection invokes transaction
  14; its registration Stub accepts transaction 1. This is wire-contract evidence,
  not a claim that the third-party mirror is an authenticated Xiaomi release.
- [MIT clean-room client](https://github.com/OrPudding/XMS_Wearable_SDK_CleanRoom/tree/6483f939785e9c1dd011465d573931f669a6adab)
  independently registers a retained service-connection Binder in `WearableClient`.
- Vela interconnect reference: `system.interconnect` error 1006 is disconnected;
  package name and release certificate must match. The existing package and
  certificate are preserved for APK and RPK.

## Validation and limits

Robolectric `XmsClientTest` forces generated AIDL Proxy/Stub calls through Parcel
serialization. It checks registration order, the one-way slot-1 acknowledgement,
hello/ready and account/saved round trips through `SyncSession`, rejected
registration, and reconnection with stale incoming callbacks discarded.
The fake service allows outgoing packets without registration but denies reverse
delivery until registration and acknowledgement, modeling the reported symptom.
Removing registration or acknowledgement handling causes the registration test
to fail separately. This proves the missing contract steps are now covered; it
does not prove that all real Mi Fitness versions require the same routing state.

Actual Mi Fitness service internals and Watch 6 Bluetooth cannot run in this cloud.
The user must confirm the physical round trip after updating. Neither a send
callback nor the test fixture is treated as proof of hardware installation/save.
No account is sent before a matching ready response; success still requires each
watch persistent-save acknowledgement. Existing UI, keys and account formats stay
compatible, so update in place rather than uninstalling.
