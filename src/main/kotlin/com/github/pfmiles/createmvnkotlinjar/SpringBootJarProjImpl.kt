package com.github.pfmiles.createmvnkotlinjar

import com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient.AsyncHttpClientDownloadParam
import com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient.AsyncHttpClientDownloadUtil
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.streams.toList

object SpringBootJarProjImpl {
    fun generate(groupId: String = "com.github.pfmiles", artifactId: String = "test-project", kotlinVersion: String = "1.9.21", javaVersion: String = "1.8", mainClsName: String = "${groupId}.HelloKt") {

        println("Creating maven kotlin spring-boot standalone jar project with parameters: groupId: $groupId, artifactId: $artifactId, koitlinVersion: $kotlinVersion, javaVersion: $javaVersion, mainClsName: $mainClsName")

        createProjSkeletonByInitializr(groupId, artifactId)

        File("./${artifactId}/pom.xml").bufferedWriter(Charsets.UTF_8).use {
            it.write(
                """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>2.7.18</version>
                            <relativePath/> <!-- lookup parent from repository -->
                        </parent>
                        <groupId>$groupId</groupId>
                        <artifactId>$artifactId</artifactId>
                        <version>1.0-SNAPSHOT</version>
                        <name>$artifactId</name>
                        <description>Demo project for Spring Boot</description>
                        <properties>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            <kotlin.version>$kotlinVersion</kotlin.version>
                            <kotlin.code.style>official</kotlin.code.style>
                            <java.version>$javaVersion</java.version>
                            <arrow.kt.version>1.2.1</arrow.kt.version>
                            <start.class>$mainClsName</start.class>
                        </properties>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-test</artifactId>
                                <scope>test</scope>
                            </dependency>
                    
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
                            <!-- kotlin arrow(functional) deps start -->
                            <dependency>
                                <groupId>io.arrow-kt</groupId>
                                <artifactId>arrow-core</artifactId>
                                <version>${'$'}{arrow.kt.version}</version>
                            </dependency>
                            <!-- kotlin arrow(functional) deps end -->
                    
                            <!-- logging deps start -->
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>1.7.36</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.logging.log4j</groupId>
                                <artifactId>log4j-slf4j-impl</artifactId>
                                <version>2.21.1</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.logging.log4j</groupId>
                                <artifactId>log4j-core</artifactId>
                                <version>2.21.1</version>
                            </dependency>
                            <dependency>
                                <groupId>com.lmax</groupId>
                                <artifactId>disruptor</artifactId>
                                <version>3.4.4</version>
                            </dependency>
                            <!-- logging deps end -->
                    
                        </dependencies>
                    
                        <build>
                            <sourceDirectory>${'$'}{project.basedir}/src/main/kotlin</sourceDirectory>
                            <testSourceDirectory>${'$'}{project.basedir}/src/test/kotlin</testSourceDirectory>
                            <plugins>
                                <plugin>
                                    <groupId>org.jetbrains.kotlin</groupId>
                                    <artifactId>kotlin-maven-plugin</artifactId>
                                    <version>${'$'}{kotlin.version}</version>
                                    <configuration>
                                        <jvmTarget>${'$'}{java.version}</jvmTarget>
                                        <args>
                                            <arg>-Xjsr305=strict</arg>
                                        </args>
                                        <compilerPlugins>
                                            <plugin>spring</plugin>
                                        </compilerPlugins>
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
                                    <version>3.10.1</version>
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
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-maven-plugin</artifactId>
                                    <configuration>
                                        <mainClass>${'$'}{start.class}</mainClass>
                                    </configuration>
                                </plugin>
                                <plugin>
                                    <groupId>org.apache.maven.plugins</groupId>
                                    <artifactId>maven-deploy-plugin</artifactId>
                                    <configuration>
                                        <skip>true</skip>
                                    </configuration>
                                </plugin>
                            </plugins>
                        </build>
                    </project>
                """.trimIndent()
            )
        }
        // rename the generated main class file to 'Hello.kt'
        val mainFile = File("./${artifactId}/src/main/kotlin/${groupId.replace('.', '/')}/Hello.kt")
        val files = Files.list(File("./${artifactId}/src/main/kotlin/${groupId.replace('.', '/')}/").toPath()).toList()
        check(files.size == 1) { "Unexpected files listed: $files" }
        Files.move(files[0], mainFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        mainFile.bufferedWriter(Charsets.UTF_8).use {
            it.write(
                """
                    package $groupId
        
                    import org.springframework.boot.autoconfigure.SpringBootApplication
                    import org.springframework.boot.runApplication
        
                    @SpringBootApplication
                    class Hello
        
                    fun main(args: Array<String>) {
                        runApplication<Hello>(*args)
                        println("Hello World!")
                    }
                """.trimIndent()
            )
        }
    }

    private fun createProjSkeletonByInitializr(groupId: String, artifactId: String) {
        val tmpFile = File.createTempFile("spring-boot-archetype", ".zip")
        try {
            AsyncHttpClientDownloadUtil.download(AsyncHttpClientDownloadParam().apply {
                this.url = "https://start.spring.io/starter.zip?type=maven-project&language=kotlin&bootVersion=3.2.3&baseDir=$artifactId&groupId=$groupId&artifactId=$artifactId&name=$artifactId&description=Demo%20project%20for%20Spring%20Boot&packageName=$groupId&packaging=jar&javaVersion=17"
                this.headers = mutableListOf(
                    "sec-ch-ua" to "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"",
                    "Referer" to "https://start.spring.io/",
                    "sec-ch-ua-mobile" to "?0",
                    "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    "sec-ch-ua-platform" to "\"macOS\""
                )
                this.targetFile = tmpFile
                this.maxFileSize = 10485760
            }).get().let {
                check(it.errCode == 0) { "Download template project from star.spring.io failed, errCode: ${it.errCode}, errMsg: ${it.errMsg}" }
                // unzip the template proj zip to current dir
                ZipInputStream(it.file.inputStream(), Charsets.UTF_8).use {
                    var entry = it.nextEntry
                    while (entry != null) {
                        if (entry.isDirectory) {
                            File(entry.name).mkdir()
                        } else {
                            val f = File(entry.name)
                            f.createNewFile()
                            IOUtils.copy(it, f.outputStream())
                        }
                        entry = it.nextEntry
                    }
                }
                println("Download project template from start.spring.io success!")
            }
        } finally {
            FileUtils.deleteQuietly(tmpFile)
            AsyncHttpClientDownloadUtil.shutdown()
        }
    }
}