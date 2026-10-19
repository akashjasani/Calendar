# mqa screen tags, uat build only (skill android-screen-tags, rule 7). Never add this file to release.
# Every screen tag in this app is a resource id name (res/values/mqa_screen_tags.xml), not a class name read at
# run time, so R8 cannot rename it and no -keepnames rule is needed. If a tag is ever built from a class name
# (Class.getSimpleName), add the matching rule here, for example:
# -keepnames class * extends androidx.fragment.app.Fragment
