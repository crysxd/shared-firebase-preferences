[![](https://jitpack.io/v/crysxd/shared-firebase-preferences.svg)](https://jitpack.io/#crysxd/shared-firebase-preferences)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/c1aa6c6b83b64707bede24dee5ca6601)](https://www.codacy.com/app/crysxd/shared-firebase-preferences?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=crysxd/shared-firebase-preferences&amp;utm_campaign=Badge_Grade)

# About
A implementation of `SharedPreferences` which syncs with Firebase database to keep your `SharedPreferences` in sync between multiple devices of one user and/or to back them up for app re-installs!

The usage is pretty simple and straight forward if you have ever worked with `SharedPreferences`. You can also easily use them with `PreferenceFragment`.

This project contains a exmaple app, take a look how you can use `SharedFirebasePreferences`!

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
  compile 'com.github.crysxd:shared-firebase-preferences:1.0.1'
}
```
# Firebase Setup
As this library uses Firebase, please follow [this](https://firebase.google.com/docs/android/setup) guide to setup Firebase in your project. Please note that your users must be signed in with Firebase to use the `SharedFirebasePreferences` properly (you still can use anonymous login at Firebase, but this would destroy the basis for this library), sou you should also follow [this](https://firebase.google.com/docs/auth/android/start/) and setup a working login for your users.

# Get a Instance
To get a instance of `SharedFirebasePreferences`, simply call `SharedFirebasePreferences.getInstance(this, "app_settings", Context.MODE_PRIVATE)` where `this` is a `Context`and `app_settings`is the name of the preferences. You can also call `SharedFirebasePreferences.getDefaultInstance(this)` to get the default instance e.g. used by `PreferenceFragment`. Please note that `FirebaseAuth.getInstance().getCurrentUser()` must not be null when getting an instance! This means a user must be signed in with Firebase.

# Sync Data
Simply call `SharedFirebasePreferences#pull()` to get the lastest values from the server. Note that you can add a `OnFetchCompleteListener`to the returned object to get updates about the pulling e.g. when it is completed. You can use `SharedFirebasePreferences#push()` to push the local data to the server. This method returns a `Task<Void>` to which listeners can be attached. Also calling `prefs.edit().put("greeting", "Hello World!").apply()` or `prefs.edit().put("greeting", "Hello World!").commit()` will automatically push the changes to the server.

You can use `SharedFirebasePreferences#keepSynced(true)` to keep the data in-sync with the server while the app is running. You will be informed about changes via the `SharedPreferences.OnSharedPreferenceChangeListener` attached to the preferences. Please remember to call `SharedFirebasePreferences#keepSynced(false)` when your app/activity enters the background!

# Omit Values
You can call `omitKeys(String...)` on any `SharedFirebasePrefernces` to omit certain keys from being pushed to Firebase. This may be handy if e.g certain keys contain sensitiv user data or should be limited for one install time.

# Use with PreferenceFragment
You must override the `attachBaseContext(Context newBase)`  method in the `Activity` hosting the `PreferenceFragment` to use `SharedFirebasePreferences` with it:

```
@Override
protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(new SharedFirebasePreferencesContextWrapper(newBase));
}
 ```

Then simply attach the `PreferenceFragment` to the activity as usual, it will use a `SharedFirebasePreference` instance to store and receive the preferences! Changes made to the prefernces by the user will be instantely synced with the database.

# Database Strucutre
As default the preferences are stored in your database at `/shared_prefs/$uid/$name` where `$uid` is the uid of the user signed in and `$name` is the name passed to `SharedFirebasePreferences.getInstance(Context, String, int)`. You can adjust this path by calling `SharedFirebasePreferences#setPathPattern(String)` like this:

```
SharedFirebasePreferences.setPathPattern(String.format(Locale.ENGLISH, "users/%s/shared_prefs/%s", UID_PLACEHOLDER, NAME_PLACEHOLDER));
```
Please note that the pattern is not applied to `SharedFirebasePreferences` instances already created with `SharedFirebasePreferences.getInstance(...)` in the past. All `.`, `#`, `$`, `[` and `]` int the path (including the name) will be replace with `-` in order to satisfy Firebase's requirements for paths in the database. This means the `SharedFirebasePrefernces` called `com.test.prefs` and `com-test-prefs` will be the same!

# Securing your Data
It is strongly recommended to secure the user's data in your Firebase database using rules. You can use these rules for the default path pattern:

```
{
  "rules": {
    ".read": "false",
    ".write": "false",
    "shared_prefs": {
      "$uid": {
        ".write": "$uid === auth.uid",
        ".read": "$uid === auth.uid"
      }
    }
  }
}
```
This set of rules allows users only to read and write to their `/shared_prefs/$uid` node.
