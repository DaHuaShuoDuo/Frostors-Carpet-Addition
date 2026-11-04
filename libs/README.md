# Development dependencies

JAR files in this directory are ignored by Git and are not distributed with FCA.

Before building, download the Carpet and Quick Shulker releases matching the target
Minecraft version and place them here with these exact names:

- `fabric-carpet-26.2.jar`
- `fabric-carpet-26.3.jar`
- `quickshulker-26.2.jar` from `MoRanpcy/quickshulker` tag `3.1.0-26.2`
- `quickshulker-26.3.jar` from `MoRanpcy/quickshulker` tag `3.1.1-26.3`

Carpet and Quick Shulker are compile-only dependencies. They are not bundled into the FCA
artifact and must be installed separately when their integrations are used.
