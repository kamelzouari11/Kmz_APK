// Gradle init script to configure KAPT for Java 11+ module system
gradle.allprojects { project ->
    project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.Kapt).configureEach {
        javaCompilerOptions {
            javac {
                option("--add-opens")
                option("java.base/java.lang=ALL-UNNAMED")
                option("--add-opens")
                option("java.base/java.lang.reflect=ALL-UNNAMED")
                option("--add-opens")
                option("jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
            }
        }
    }
}
