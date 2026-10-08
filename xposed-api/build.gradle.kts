plugins {
    id("java-library")
}

// Compile-time stubs only. See the package README in src/ for why these are vendored; the app
// depends on this module with compileOnly, so nothing here is ever packaged.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
