* A handy standalone jar program to facilitate creating kotlin single jar projects.
* 'mvn' command needed in $PATH
* Java 1.8 or later runtime required
* Java source code is also allowed
* usage:
    1. java -jar createMvnKotlinJar-1.0.0-shaded.jar groupId artifactId kotlinVersion(optional) javaVersion(optional)
       mainClsName(optional, full-qualified-class-name)
    2. cd ${artifactId}
    3. mvn clean install
    4. java -jar target/${artifactId}-1.0-SNAPSHOT-shaded.jar, then you can see 'Hello, World' out there.