[![](https://jitpack.io/v/crysxd/SharedFirebasePreferences.svg)](https://jitpack.io/#crysxd/SharedFirebasePreferences)

# About
A implementation of SharePreferences which syncs with Firebase database to keep your SharedPreferences in sync between multiple devices of one user and/or to back them up for app re-installs!

The usage is pretty simple and straight forward if you have ever worked with SharedPreferences. You can also easily use them with PreferenceFragment.

# Setup
Add this to your project level build.gradle:
```
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

Add this to your app level build.gradle:
```
dependencies {
  compile 'com.github.crysxd:SharedFirebasePreferences:v1.0.0'
}
```
# Firebase Setup
As this library uses Firebase, please follow [this](https://firebase.google.com/docs/android/setup) guide to setup Firebase in your project. Please note that your users must be signed in with Firebase to use the SharedFirebasePreferences properly (you still can use anonymous login at Firebase, but this would destroy the basis for this library), sou you should also follow [this](https://firebase.google.com/docs/auth/android/start/) and setup a working login for your users.
