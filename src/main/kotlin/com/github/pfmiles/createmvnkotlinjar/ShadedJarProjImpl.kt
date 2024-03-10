package com.github.pfmiles.createmvnkotlinjar

import org.apache.commons.io.IOUtils
import java.io.File

object ShadedJarProjImpl {
    fun generate(groupId: String = "com.github.pfmiles", artifactId: String = "test-project", kotlinVersion: String = "1.9.21", javaVersion: String = "1.8", mainClsName: String = "${groupId}.HelloKt") {

        println("Creating maven kotlin jar project with parameters: groupId: $groupId, artifactId: $artifactId, koitlinVersion: $kotlinVersion, javaVersion: $javaVersion, mainClsName: $mainClsName")

        createProjSkeletonUsingMvn(groupId, artifactId, kotlinVersion)

        File("./${artifactId}/pom.xml").bufferedWriter(Charsets.UTF_8).use {
            it.write(
                """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
                    
                        <modelVersion>4.0.0</modelVersion>
                    
                        <groupId>${groupId}</groupId>
                        <artifactId>${artifactId}</artifactId>
                        <version>1.0-SNAPSHOT</version>
                        <packaging>jar</packaging>
                    
                        <name>${artifactId}</name>
                        <description>Project description.</description>
                    
                        <properties>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            <kotlin.version>${kotlinVersion}</kotlin.version>
                            <kotlin.code.style>official</kotlin.code.style>
                            <java.version>${javaVersion}</java.version>
                            <main.cls.name>${mainClsName}</main.cls.name>
                        </properties>
                    
                        <dependencies>
                            <!-- kotlin deps start -->
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-stdlib</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-stdlib-common</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-stdlib-jdk8</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-stdlib-jdk7</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-reflect</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-test-junit</artifactId>
                                <version>${'$'}{kotlin.version}</version>
                                <scope>test</scope>
                            </dependency>
                            <!-- kotlin deps end -->
                    
                        </dependencies>
                    
                        <build>
                            <sourceDirectory>src/main/kotlin</sourceDirectory>
                            <testSourceDirectory>src/test/kotlin</testSourceDirectory>
                    
                            <plugins>
                                <plugin>
                                    <groupId>org.jetbrains.kotlin</groupId>
                                    <artifactId>kotlin-maven-plugin</artifactId>
                                    <version>${'$'}{kotlin.version}</version>
                                    <configuration>
                                        <jvmTarget>${'$'}{java.version}</jvmTarget>
                                    </configuration>
                                    <dependencies>
                                        <dependency>
                                            <groupId>org.jetbrains.kotlin</groupId>
                                            <artifactId>kotlin-maven-allopen</artifactId>
                                            <version>${'$'}{kotlin.version}</version>
                                        </dependency>
                                    </dependencies>
                                    <executions>
                                        <execution>
                                            <id>compile</id>
                                            <phase>compile</phase>
                                            <goals>
                                                <goal>compile</goal>
                                            </goals>
                                        </execution>
                                        <execution>
                                            <id>test-compile</id>
                                            <phase>test-compile</phase>
                                            <goals>
                                                <goal>test-compile</goal>
                                            </goals>
                                        </execution>
                                    </executions>
                                </plugin>
                                <plugin>
                                    <groupId>org.apache.maven.plugins</groupId>
                                    <artifactId>maven-compiler-plugin</artifactId>
                                    <version>3.12.1</version>
                                    <configuration>
                                        <source>${'$'}{java.version}</source>
                                        <target>${'$'}{java.version}</target>
                                        <encoding>UTF-8</encoding>
                                    </configuration>
                                    <executions>
                                        <!-- Replacing default-compile as it is treated specially by Maven -->
                                        <execution>
                                            <id>default-compile</id>
                                            <phase>none</phase>
                                        </execution>
                                        <!-- Replacing default-testCompile as it is treated specially by Maven -->
                                        <execution>
                                            <id>default-testCompile</id>
                                            <phase>none</phase>
                                        </execution>
                                        <execution>
                                            <id>java-compile</id>
                                            <phase>compile</phase>
                                            <goals>
                                                <goal>compile</goal>
                                            </goals>
                                        </execution>
                                        <execution>
                                            <id>java-test-compile</id>
                                            <phase>test-compile</phase>
                                            <goals>
                                                <goal>testCompile</goal>
                                            </goals>
                                        </execution>
                                    </executions>
                                </plugin>
                                <plugin>
                                    <groupId>org.codehaus.mojo</groupId>
                                    <artifactId>exec-maven-plugin</artifactId>
                                    <version>3.2.0</version>
                                    <executions>
                                        <execution>
                                            <goals>
                                                <goal>exec</goal>
                                            </goals>
                                        </execution>
                                    </executions>
                                    <configuration>
                                        <mainClass>${'$'}{main.cls.name}</mainClass>
                                    </configuration>
                                </plugin>
                                <plugin>
                                    <groupId>org.apache.maven.plugins</groupId>
                                    <artifactId>maven-shade-plugin</artifactId>
                                    <version>3.5.2</version>
                                    <executions>
                                        <execution>
                                            <goals>
                                                <goal>shade</goal>
                                            </goals>
                                            <configuration>
                                                <shadedArtifactAttached>true</shadedArtifactAttached>
                                                <transformers>
                                                    <transformer
                                                            implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                                        <mainClass>${'$'}{main.cls.name}</mainClass>
                                                    </transformer>
                                                </transformers>
                                            </configuration>
                                        </execution>
                                    </executions>
                                </plugin>
                            </plugins>
                        </build>
                    
                    </project>
                """.trimIndent()
            )
        }
    }

    // create mvn proj in current dir
    private fun createProjSkeletonUsingMvn(groupId: String, artifactId: String, kotlinVersion: String) {
        val cmd = """
                mvn -X archetype:generate -DarchetypeGroupId=org.jetbrains.kotlin
                    -DarchetypeArtifactId=kotlin-archetype-jvm
                    -DarchetypeVersion=${kotlinVersion}
                    -DgroupId=${groupId}
                    -DartifactId=${artifactId}
                    -Dversion=1.0-SNAPSHOT
                    -DinteractiveMode=false
            """.trimIndent().replace('\n', ' ').trim()
        Runtime.getRuntime().exec(cmd).let {
            val printStdOut = Thread({
                it.inputStream.bufferedReader(Charsets.UTF_8).use { it.forEachLine(::println) }
            }, "mvn-cmd-stdout-printer")
            printStdOut.start()
            val printStdErr = Thread({
                it.errorStream.bufferedReader(Charsets.UTF_8).use { it.forEachLine(::println) }
            }, "mvn-cmd-stderr-printer")
            printStdErr.start()
            try {
                check(it.waitFor() == 0) {
                    "Create maven kotlin jar project error, please refer to the output message for more info."
                }
            } finally {
                IOUtils.closeQuietly(it.getInputStream())
                IOUtils.closeQuietly(it.getOutputStream())
                IOUtils.closeQuietly(it.getErrorStream())
            }
        }
    }
}