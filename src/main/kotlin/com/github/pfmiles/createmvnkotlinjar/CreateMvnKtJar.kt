package com.github.pfmiles.createmvnkotlinjar

/**
 * @author pf-miles
 */
class CreateMvnKtJar {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) {
                println("usage: CreateMvnKtJar groupId artifactId kotlinVersion(optional) javaVersion(optional) mainClsName(optional, full-qualified-class-name)")
                System.exit(1)
            }
            val groupId = args.getOrElse(0) { "com.github.pfmiles" }
            val artifactId = args.getOrElse(1) { "test-project" }
            val kotlinVersion = args.getOrElse(2) { "1.9.21" }
            val javaVersion = args.getOrElse(3) { "1.8" }
            val mainClsName = args.getOrElse(4) { "${groupId}.HelloKt" }

            if ("springboot".equals(System.getProperty("type"))) {
                SpringBootJarProjImpl.generate(groupId, artifactId, kotlinVersion, javaVersion, mainClsName)
            } else {
                ShadedJarProjImpl.generate(groupId, artifactId, kotlinVersion, javaVersion, mainClsName)
            }
        }

    }
}