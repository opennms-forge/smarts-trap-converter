# smarts-trap-converter

## Overview

This tool can be used to generate and enrich OpenNMS event definitions from Smarts trap configuration.

Given the Smarts trap configuration and the existing OpenNMS event definitions it can:
* Parse the Smarts trap configuration
    * See src/test/resources/smarts_trap_defs for a snippet of such a file
* Load the OpenNMS event definitions
* Match the Smarts trap definitions with the corresponding OpenNMS event definitions
* Generate event definitions using the Smarts trap definitions (enriched with MIB data if available)
* Enrich existing event definitions with the data from the Smarts trap definitions

## Requirements

* Maven 3
* Java 8
* Smarts trap configuration
* OpenNMS event definitions

## Usage

First, compile the fat jar with:
```sh
mvn package
```

And define all of the paths and settings in a file named `smart-converter.yaml`, i.e.:
```yaml
smarts:
  # Full path to the Smarts trap configuration
  traps: /tmp/smarts_traps
  # Optionally ignore definition where the enterprise starts with the given prefix
  excludes:
    - enterprise-prefix: "*"
      reason: Match all
opennms:
  # Full path to the OpenNMS event configuration
  events: /opt/opennms/etc/eventconf.xml
output:
  # Folder in which to output any generated events. It is assumed to already exist.
  events: /tmp/events
mibs:
  # Folder in which to find any referenced MIBs.
  dir: /tmp/mibs
  # Map enterprise prefixes to MIBs and use the MIB data during the event generation process
  mappings:
    - enterprise-prefix:
        - .1.3.6.1.2.1.51.3
      mibs:
        - RSVP-MIB.my
      uei-prefix: uei.opennms.org/IETF/RSVP/traps
      target: ietf.rsvp.events.xml
```

### Generating

```sh
java -jar target/smarts-trap-converter-1.0-SNAPSHOT-jar-with-dependencies.jar generate -c smarts-converter.yaml
```

### Auditing

```sh
java -jar target/smarts-trap-converter-1.0-SNAPSHOT-jar-with-dependencies.jar audit -c smarts-converter.yaml
```
