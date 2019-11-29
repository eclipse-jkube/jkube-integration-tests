Spring-Boot with multiple profiles example
==========================================

Example Spring-Boot application which exposes a single endpoint.

Depending on the active profile, application port and response varies depending on the
active profile:
 - Default: http://localhost:8081/ -> `MultiProfile default`
 - prod: http://localhost:8080/ -> `MultiProfile prod`
 
For `spring-boot:run` maven goal you can switch profiles with the following command:

`mvn spring-boot:run -Dspring-boot.run.profiles=prod`
