# ActionScript Gradle Plugin

Gradle support for arguably the worst language and platform in the world.
Hopefully this is better than FlashDevelop and likes.

This plugin allows you to compile ActionScript code using the Adobe AIR SDK. 

## Installation

To use this plugin, add Maven Central Snapshots repository to your `settings.gradle.kts`:

```kotlin
pluginManagement {
  repositories {
    // ...
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
      name = "Central Portal Snapshots"
      content {
        includeModule("dev.assasans.actionscript", "dev.assasans.actionscript.gradle.plugin")
        includeModule("dev.assasans.actionscript", "gradle-actionscript-plugin")
      }
    }
  }
}
```

Then, apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
  id("dev.assasans.actionscript") version "1.0.5-SNAPSHOT"
}
```

## Example usage

Set `dev.assasans.actionscript.sdk` property to the path of the Adobe AIR SDK. This can be done in `gradle.properties`:

```properties
dev.assasans.actionscript.sdk=/path/to/your/AIR_SDK
```

```kotlin
plugins {
  id("dev.assasans.actionscript") version "1.0.3-SNAPSHOT"
}

actionscript {
  // Add source directory
  source("src")
  
  // Load compiler configuration file
  config("config.xml")

  // Set custom compiler options
  option("-default-size=1024,768")

  // Generate .swc file to allow usage as a library in other projects
  swc = true
  
  // Generate .swf file to allow running in a Flash Player
  swf = SwfType.Entry
  
  // Set main class for the .swf file
  mainClass = "com.example.Main"
}
```

## License
Licensed under the MIT license ([LICENSE](LICENSE) or https://opensource.org/license/MIT).

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you, as defined in the MIT license,
shall be licensed as above, without any additional terms or conditions.
