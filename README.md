[![](https://jitpack.io/v/crysxd/SharedFirebasePreferences.svg)](https://jitpack.io/#crysxd/SharedFirebasePreferences)

# About
A implementation of `SharedPreferences` which syncs with Firebase database to keep your `SharedPreferences` in sync between multiple devices of one user and/or to back them up for app re-installs!

The usage is pretty simple and straight forward if you have ever worked with `SharedPreferences`. You can also easily use them with `PreferenceFragment`.

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
As this library uses Firebase, please follow [this](https://firebase.google.com/docs/android/setup) guide to setup Firebase in your project. Please note that your users must be signed in with Firebase to use the `SharedFirebasePreferences` properly (you still can use anonymous login at Firebase, but this would destroy the basis for this library), sou you should also follow [this](https://firebase.google.com/docs/auth/android/start/) and setup a working login for your users.

# Get a Instance
To get a instance of `SharedFirebasePreferences`, simply call `SharedFirebasePreferences.getInstance(this, "app_settings", Context.MODE_PRIVATE)` where `this` is a `Context`and `app_settings`is the name of the preferences. You can also call `SharedFirebasePreferences.getDefaultInstance(this)` to get the default instance e.g. used by `PreferenceFragment`. Please note that `FirebaseAuth.getInstance().getCurrentUser()` must not be null when getting an instance! This means a user must be signed in with Firebase.

# Sync Data
Simply call `SharedFirebasePreferences#pull()` to get the lastest values from the server. Note that you can add a `OnFetchCompleteListener`to the returned object to get updates about the pulling e.g. when it is completed. You can use `SharedFirebasePreferences#push()` to push the local data to the server. This method returns a `Task<Void>` to which listeners can be attached. Also calling `prefs.edit().put("greeting", "Hello World!").apply()` or `prefs.edit().put("greeting", "Hello World!").commit()` will automatically push the changes to the server.

You can use `SharedFirebasePreferences#keepSynced(true)` to keep the data in-sync with the server while the app is running. You will be informed about changes via the `SharedPreferences.OnSharedPreferenceChangeListener` attached to the preferences. Please remember to call `SharedFirebasePreferences#keepSynced(false)` when your app/activity enters the abckground!

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

# Securing your Data

# 
