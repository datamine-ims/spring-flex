# Spring Flex Core with Spring 7.0.8 and hibernate jakarta 7.4.2.Final

This branch is a part of CCLAS's in-house work to ensure compatibility between `spring-flex-core` , `spring-flex-hibernate7` and `spring 7.0.8`.

The modules `spring-flex-core` , `spring-flex-hibernate7` and `spring-flex-parent` are built and deployed into our Nexus repository located at `http://172.22.22.9/nexus/content/repositories/thirdparty`.

To resolve this library into your local CCLAS project, you may need to add the following repository setting into your `.m2/settings.xml`:

```xml
<repository>
    <id>nexus-3rd-party</id>
    <name>Datamine Nexus 3rd Party</name>
    <url>http://172.22.22.9/nexus/content/repositories/thirdparty</url>
</repository>