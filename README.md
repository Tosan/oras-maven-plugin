# Oras Maven Plugin

This is a Maven plugin for packaging and uploading artifacts to oci registry such as docker repository.

Visit <https://oras.land/> for detailed information.

## Usage

the plugin use the oras executable file in the OS path.

Add following dependency to your pom.xml:

```xml

<dependency>
    <groupId>com.tosan.plugin</groupId>
    <artifactId>oras-maven-plugin</artifactId>
    <version>${oras.plugin.version}</version>
</dependency>
```

## Configuration Examples

The following is an example configuration that explicitly sets the directory in which to look for the `oras` executable:

```xml

<build>
    <plugins>
        ...
        <plugin>
            <groupId>com.tosan.plugin</groupId>
            <artifactId>oras-maven-plugin</artifactId>
            <version>${oras.plugin.version}</version>
            <configuration>
                <workingDirectory>${project.basedir}</workingDirectory>
                <artifacts>
                    <artifact>test-dir</artifact>
                </artifacts>
                <!-- Optional. This is the related section to use specific oras binary -->
                <executableDirectory>/usr/local/bin</executableDirectory>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

### Configure Plugin to Use Credentials from settings.xml for Push

```xml

<build>
    <plugins>
        ...
        <plugin>
            <groupId>com.tosan.plugin</groupId>
            <artifactId>oras-maven-plugin</artifactId>
            <version>${oras.plugin.version}</version>
            <configuration>
                <workingDirectory>${project.basedir}</workingDirectory>
                <artifacts>
                    <artifact>test-dir</artifact>
                </artifacts>
                <!-- This is the related section to configure upload repos -->
                <stableRepository>
                    <name>registry-in-setting</name>
                    <url>${docker.registry}/oras</url>
                </stableRepository>
                <snapshotRepository>
                    <name>registry-in-setting</name>
                    <url>${docker.registry}/oras</url>
                </snapshotRepository>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

## Features

- Package artifacts from standard folder structure as a tgz file
- Push to OCI registry such as docker repository
- Repository names are interpreted as server IDs to retrieve basic authentication from server list in settings.xml.

## Goals

- `oras:package-push` packages the given artifacts to tgz format and push them to OCI (docker registry)
- `oras:push` push artifacts to OCI (docker registry)

## Configuration

| Parameter               | Type                                                                     | User Property            | Required | Description                                                                                                                               |
|-------------------------|--------------------------------------------------------------------------|--------------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `<executableDirectory>` | string                                                                   | oras.executableDirectory | false    | directory of your oras installation (default:OS PATH)                                                                                     |
| `<outputDirectory>`     | string                                                                   | oras.outputDirectory     | false    | artifacts output directory (default: `${project.build.directory}/oras`)                                                                   |
| `<workingDirectory>`    | string                                                                   | oras.workingDirectory    | true     | root directory of your artifacts                                                                                                          |
| `<artifacts>`           | list of strings                                                          | oras.artifacts           | true     | list of artifacts to include.                                                                                                             |
| `<excludes>`            | list of strings                                                          | oras.excludes            | false    | list of artifacts to exclude.                                                                                                             |
| `<artifactType>`        | string                                                                   | oras.artifactType        | false    | artifact type.                                                                                                                            |
| `<uploadName>`          | string                                                                   | oras.uploadName          | false    | The name of the app to be upload.                                                                                                         |
| `<uploadVersion>`       | string                                                                   | oras.uploadVersion       | false    | The version of the app to be upload.                                                                                                      |
| `<stableRepository>`    | [OCIRegistry](src/main/java/com/tosan/plugin/oras/util/OCIRegistry.java) | oci.stable               | true     | Upload registry for stable artifacts                                                                                                      |
| `<snapshotRepository>`  | [OCIRegistry](src/main/java/com/tosan/plugin/oras/util/OCIRegistry.java) | oci.snapshot             | false    | Upload registry for snapshot artifacts (determined by version postfix 'SNAPSHOT')                                                         |
| `<ociSecurity>`         | string                                                                   | oci.security             | false    | path to your [settings-security.xml](https://maven.apache.org/guides/mini/guide-encryption.html) (default: `~/.m2/settings-security.xml`) |
| `<insecure>`            | boolean                                                                  | oras.insecure            | false    | allow connections to SSL registry without certs (default: `false`)                                                                        |
| `<skip>`                | boolean                                                                  | oras.skip                | false    | skip plugin execution                                                                                                                     |
| `<skipPush>`            | boolean                                                                  | oras.upload.skip         | false    | skip push to goals                                                                                                                        |
| `<debug>`               | boolean                                                                  | oras.debug               | false    | debug mode (default: `false`)                                                                                                             |
| `<verbose>`             | boolean                                                                  | oras.verbose             | false    | verbose output (default: `false`)                                                                                                         |
