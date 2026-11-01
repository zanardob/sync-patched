group = "app.revanced"

patches {
    about {
        name = "Sync for Reddit patches"
        description = "Personal ReVanced patches for Sync for Reddit"
        source = "git@github.com:zanardob/sync-patched.git"
        author = "zanardob"
        contact = "zanardob@users.noreply.github.com"
        website = "https://github.com/zanardob/sync-patched"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // smali needs Guava at build time.
    implementation(libs.guava)
}
