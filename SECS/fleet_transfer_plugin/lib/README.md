# Installed Fleet transport dependency

`secs-2.11.0.jar` was copied from this installed Fleet ECS inspection artifact. It is used for the custom plugin's opt-in HSMS transport. No vendor server classes are changed. `package_runtime.py` includes only `com/kuka/ecs/common/transport/secs/` entries, not ECS application classes. SDK/gRPC dependencies are bundled as before.

This package is for this Fleet installation and internal testing; it is not presented as an independently licensed public redistribution. Preserve the vendor installation and applicable licensing terms.
