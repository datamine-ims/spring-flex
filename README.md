# Spring Flex Core with Spring 6.2.8 and hibernate jakarta 5.6.15

This branch is a part of CCLAS's in-house work to ensure compatibility between `spring-flex-core` , `spring-flex-hibernate5` and `spring 6.2.8`.

The modules `spring-flex-core` , `spring-flex-hibernate5` and `spring-flex-parent` are built and deployed into our Nexus repository located at `http://172.22.22.9/nexus/content/repositories/thirdparty`.

To resolve this library into your local CCLAS project, you may need to add the following repository setting into your `.m2/settings.xml`:

```xml
<repository>
    <id>nexus-3rd-party</id>
    <name>Datamine Nexus 3rd Party</name>
    <url>http://172.22.22.9/nexus/content/repositories/thirdparty</url>
</repository>