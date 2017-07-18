package sharefirebasepreferences.crysxd.de.lib;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A {@link SharedPreferences} implementation which syncs all data with firebase. Use {@link #getInstance(Context, String, int)}
 * to receive a instance
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SharedFirebasePreferences implements SharedPreferences {

    /**
     * The placeholder in {@link #sPathPattern} for the preferences' names
     */
    public static final String NAME_PLACEHOLDER = "$name";

    /**
     * The placeholder in {@link #sPathPattern} for the user's id
     */
    public static final String UID_PLACEHOLDER = "$uid";

    /**
     * The log tag
     */
    private static final String TAG = "SharedFirebasePrefs";

    /**
     * The instances
     */
    private static Map<String, SharedFirebasePreferences> sInstances = new HashMap<>();

    /**
     * The pattern for the paths to the roots of the preferences
     */
    private static String sPathPattern = String.format(Locale.ENGLISH, "/shared_prefs/%s/%s", UID_PLACEHOLDER, NAME_PLACEHOLDER);

    /**
     * The wrapped {@link SharedPreferences}
     */
    private SharedPreferences mCache;

    /**
     * The {@link DatabaseReference} which is used for storing
     */
    private DatabaseReference mRoot;

    /**
     * A list with keys which should be omitted
     */
    private List<String> mOmmitedKeys = new ArrayList<>();

    /**
     * The {@link SyncAdapter} to keep the database and the local files in sync
     */
    private SyncAdapter mSyncAdapter;

    /**
     * Creates a new instance
     *
     * @param cache the wrapped {@link SharedPreferences}
     * @param root  the {@link DatabaseReference} used for storing
     */
    protected SharedFirebasePreferences(SharedPreferences cache, DatabaseReference root) {
        mCache = cache;
        mRoot = root;
        mSyncAdapter = new SyncAdapter(this);
    }

    /**
     * Sets the path pattern used to create the paths to the preferences in the database. Use the
     * placeholders for uid and name to customize the path for each instance.
     *
     * @param patter the pattern
     * @see #NAME_PLACEHOLDER
     * @see #UID_PLACEHOLDER
     */
    public static void setPathPattern(String patter) {
        sPathPattern = patter;
    }

    /**
     * Returns a instance for the given name
     *
     * @param con  a {@link Context}
     * @param name the preferences names
     * @param mode the mode
     * @return the instance
     * @see Context#MODE_PRIVATE
     */
    public synchronized static SharedFirebasePreferences getInstance(Context con, String name, int mode) {
        return getInstance(con, name, mode, FirebaseDatabase.getInstance());
    }

    /**
     * Removes all character forbidden for firebase paths
     *
     * @param s the string to sanitize
     * @return the sanitized string
     */
    private static String sanitizeString(String s) {
        return s.replace('.', '-').replace('#', '-').replace('$', '-').replace('[', '-').replace(']', '-');
    }

    /**
     * Returns the default root for the given name
     *
     * @param name the name
     * @param db   the {@link FirebaseDatabase} to use
     * @return the {@link DatabaseReference} for the root
     */
    private static DatabaseReference getRoot(String name, FirebaseDatabase db) {
        // Check if any user is signed in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No user signed in with firebase");
        }

        return db.getReference(sanitizeString(sPathPattern.
                replace(UID_PLACEHOLDER, user.getUid()).replace(NAME_PLACEHOLDER, name)));
    }

    /**
     * Returns a instance for the given name
     *
     * @param con  a {@link Context}
     * @param name the preferences names. If the name already exists as local preferences, the data will be pushed to Firebase
     * @param mode the mode
     * @param db   the {@link FirebaseDatabase} to use
     * @return the instance
     * @see Context#MODE_PRIVATE
     */
    public synchronized static SharedFirebasePreferences getInstance(Context con, String name, int mode, FirebaseDatabase db) {
        // Check if we already have a instance, create new one if not
        // Get the prefs from Application to prevent a SharedfirebasePreferences instance is returned
        // from a context wrapped
        if (!sInstances.containsKey(name)) {
            sInstances.put(name, new SharedFirebasePreferences(con.getApplicationContext().getSharedPreferences(name, mode), getRoot(name, db)));
        }

        // Return the singleton
        return sInstances.get(name);

    }

    /**
     * Returns the default instance from {@link PreferenceManager}
     *
     * @param con a {@link Context}
     * @return the instance
     * @see Context#MODE_PRIVATE
     */
    public synchronized static SharedFirebasePreferences getDefaultInstance(Context con) {
        return (SharedFirebasePreferences) PreferenceManager.getDefaultSharedPreferences(new SharedFirebasePreferencesContextWrapper(con));
    }

    /**
     * Omits all given keys when pushing the preferences to firebase. Use this method if you want to
     * exclude a preference e.g. for security reasons. You can call this mehtod multiple times.
     *
     * @param keys all keys to be omitted.
     */
    public void omitKeys(String... keys) {
        this.mOmmitedKeys.addAll(Arrays.asList(keys));

    }

    /**
     * Fetches the latest data from Firebase
     *
     * @return the {@link PullTask}
     */
    public PullTask pull() {
        return new PullTask(this).addOnPullCompleteListener(new OnPullCompleteListener() {
            @Override
            public void onPullSucceeded(SharedFirebasePreferences preferences) {
                Log.i(TAG, "Pull of " + getRoot().toString() + " succeeded");
            }

            @Override
            public void onPullFailed(Exception e) {
                Log.e(TAG, "Pull of " + getRoot().toString() + " failed", e);
            }
        });
    }

    /**
     * Keeps the {@link SharedPreferences} in sync with the firebase database. This requires a active
     * connection to the database and should not be used in background.
     *
     * @param b true to enable syncing, false to disbale
     */
    public void keepSynced(boolean b) {
        mRoot.keepSynced(b);
        if (b) {
            mRoot.addValueEventListener(mSyncAdapter);
        } else {
            mRoot.removeEventListener(mSyncAdapter);
        }
    }

    /**
     * Pushed the current local data to Firebase
     *
     * @return the {@link Task}
     */
    public Task<Void> push() {
        return new PushTask(this).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "Push of " + getRoot().toString() + " failed", e);

            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.i(TAG, "Push of " + getRoot().toString() + " succeeded");

            }
        });
    }

    @Override
    public Map<String, ?> getAll() {
        return mCache.getAll();
    }

    @Nullable
    @Override
    public String getString(String s, @Nullable String s1) {
        return mCache.getString(s, s1);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String s, @Nullable Set<String> set) {
        return mCache.getStringSet(s, set);
    }

    @Override
    public int getInt(String s, int i) {
        return mCache.getInt(s, i);
    }

    @Override
    public long getLong(String s, long l) {
        return mCache.getLong(s, l);
    }

    @Override
    public float getFloat(String s, float v) {
        return mCache.getFloat(s, v);
    }

    @Override
    public boolean getBoolean(String s, boolean b) {
        return mCache.getBoolean(s, b);
    }

    @Override
    public boolean contains(String s) {
        return mCache.contains(s);
    }

    @Override
    public Editor edit() {
        return new Editor(this);
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mCache.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mCache.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    /**
     * Returns the {@link SharedPreferences} which is used as local cached
     *
     * @return the local cache
     */
    protected SharedPreferences getCache() {
        return mCache;
    }

    /**
     * Returns the {@link DatabaseReference}  which is the root of this preferences
     *
     * @return the root
     */
    protected DatabaseReference getRoot() {
        return mRoot;
    }

    /**
     * A listener to get notified about pull results
     */
    public interface OnPullCompleteListener {

        /**
         * Called when the pull was successful
         *
         * @param preferences the updated {@link SharedFirebasePreferences}
         */
        void onPullSucceeded(SharedFirebasePreferences preferences);

        /**
         * Called when the pull failed
         *
         * @param e the occured {@link Exception}
         */
        void onPullFailed(Exception e);

    }

    /**
     * A editor pushing changed to firebase
     */
    public static class Editor implements SharedPreferences.Editor {

        /**
         * The {@link android.content.SharedPreferences.Editor} handling the edit process
         */
        private SharedPreferences.Editor mWrapped;

        /**
         * The {@link SharedFirebasePreferences} being edited
         */
        private SharedFirebasePreferences mPrefs;

        /**
         * Creates a new instance
         *
         * @param prefs the {@link SharedFirebasePreferences} being edited
         */
        protected Editor(SharedFirebasePreferences prefs) {
            mWrapped = prefs.getCache().edit();
            mPrefs = prefs;
        }

        @Override
        public SharedPreferences.Editor putString(String s, @Nullable String s1) {
            mWrapped.putString(s, s1);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String s, @Nullable Set<String> set) {
            mWrapped.putStringSet(s, set);
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String s, int i) {
            mWrapped.putInt(s, i);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String s, long l) {
            mWrapped.putLong(s, l);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String s, float v) {
            mWrapped.putFloat(s, v);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String s, boolean b) {
            mWrapped.putBoolean(s, b);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String s) {
            mWrapped.remove(s);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            mWrapped.clear();
            return this;
        }

        @Override
        public boolean commit() {
            if (mWrapped.commit()) {
                mPrefs.push();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void apply() {
            mWrapped.apply();
            mPrefs.push();
        }
    }

    /**
     * Syncs the database and the shared preferences while active
     */
    public static class SyncAdapter implements ValueEventListener {

        /**
         * The {@link SharedFirebasePreferences} to keep in sync
         */
        private SharedFirebasePreferences mPreferences;

        /**
         * Creates a new instance
         *
         * @param preferences the {@link SharedFirebasePreferences} to keep in sync
         */
        public SyncAdapter(SharedFirebasePreferences preferences) {
            mPreferences = preferences;

        }

        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            new PullTask(mPreferences, dataSnapshot).addOnPullCompleteListener(new OnPullCompleteListener() {
                @Override
                public void onPullSucceeded(SharedFirebasePreferences preferences) {
                    Log.i(TAG, "Synced data");
                }

                @Override
                public void onPullFailed(Exception e) {
                    Log.i(TAG, "Error while syncing data", e);
                }
            });
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.e(TAG, "Error while syncing", databaseError.toException());
        }
    }

    /**
     * A task fetching the latest values from firebase
     */
    public static class PullTask implements ValueEventListener {

        /**
         * The {@link SharedFirebasePreferences} which should be fetched from Firebase
         */
        private SharedFirebasePreferences mPreferences;

        /**
         * The listeners
         */
        private List<OnPullCompleteListener> mListener = new ArrayList<>();

        /**
         * Creates a new instance
         *
         * @param preferences the {@link SharedFirebasePreferences} which should be fetched from Firebase
         */
        public PullTask(SharedFirebasePreferences preferences) {
            mPreferences = preferences;
            mPreferences.getRoot().addListenerForSingleValueEvent(this);

        }

        /**
         * Creates a new instance
         *
         * @param preferences  the {@link SharedFirebasePreferences} which should be fetched from Firebase
         * @param dataSnapshot the data snapshot from which the data should be pulled
         */
        public PullTask(SharedFirebasePreferences preferences, DataSnapshot dataSnapshot) {
            mPreferences = preferences;
            onDataChange(dataSnapshot);

        }


        /**
         * Adds a {@link OnPullCompleteListener} to get informed when the pull is completed
         *
         * @param listener the {@link OnPullCompleteListener}
         * @return this instance
         */
        public PullTask addOnPullCompleteListener(@NonNull OnPullCompleteListener listener) {
            mListener.add(listener);
            return this;
        }


        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            // Copy values into cache to prevent values to be instantly pushed to Firebase again
            try {
                SharedPreferences.Editor e = mPreferences.getCache().edit().clear();
                for (DataSnapshot s : dataSnapshot.getChildren()) {
                    Object v = s.getValue();
                    String k = s.getKey();
                    if (v instanceof String) {
                        e.putString(k, (String) v);
                    } else if (v instanceof Long) {
                        e.putLong(k, (Long) v);
                    } else if (v instanceof Integer) {
                        e.putInt(k, (Integer) v);
                    } else if (v instanceof Boolean) {
                        e.putBoolean(k, (Boolean) v);
                    } else if (v instanceof Float) {
                        e.putFloat(k, (Float) v);
                    } else if (v instanceof List) {
                        //noinspection unchecked
                        e.putStringSet(k, new HashSet<>((List<String>) v));
                    }
                }
                e.apply();
            } catch (Exception e) {
                Log.e(TAG, "Error while processing fetched data", e);
                dispatchFetchFailed(e);
            }

            // Dispatch event
            dispatchFetchSucceeded();
        }

        /**
         * Dispatches the {@link OnPullCompleteListener#onPullFailed(Exception)}
         * event for all listeners
         */
        private void dispatchFetchFailed(Exception e) {
            for (OnPullCompleteListener l : mListener) {
                try {
                    l.onPullFailed(e);

                } catch (Exception e2) {
                    Log.e(TAG, "Error while dispatching onPullFailed() event", e);

                }
            }
        }

        /**
         * Dispatches the {@link OnPullCompleteListener#onPullSucceeded(SharedFirebasePreferences)}
         * event for all listeners
         */
        private void dispatchFetchSucceeded() {
            for (OnPullCompleteListener l : mListener) {
                try {
                    l.onPullSucceeded(mPreferences);

                } catch (Exception e) {
                    Log.e(TAG, "Error while dispatching onPullSucceeded() event", e);

                }
            }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            dispatchFetchFailed(databaseError.toException());
        }
    }

    /**
     * A {@link Task} pushing the data of the given {@link SharedFirebasePreferences}
     */
    public static class PushTask extends Task<Void> {

        /**
         * The update task
         */
        private final Task<Void> mTask;

        /**
         * Creates a new instance and pushes the data of the given preferences
         *
         * @param preferences the {@link SharedFirebasePreferences} to be pushed
         */
        public PushTask(SharedFirebasePreferences preferences) {
            // Replace sets with lists to use default firebase serialization
            HashMap<String, Object> values = new HashMap<>(preferences.getAll());
            for (String k : values.keySet()) {
                if (values.get(k) instanceof Set) {
                    //noinspection unchecked
                    values.put(k, new ArrayList<>((Set<String>) values.get(k)));
                }
            }

            // Remove omitted values
            for (String key : new ArrayList<>(values.keySet())) {
                if (preferences.mOmmitedKeys.contains(key)) {
                    values.remove(key);
                }
            }

            // Start push
            mTask = preferences.getRoot().updateChildren(values);
        }

        @Override
        public boolean isComplete() {
            return mTask.isComplete();
        }

        @Override
        public boolean isSuccessful() {
            return mTask.isSuccessful();
        }

        @Override
        public Void getResult() {
            return mTask.getResult();
        }

        @Override
        public <X extends Throwable> Void getResult(@NonNull Class<X> aClass) throws X {
            return mTask.getResult(aClass);
        }

        @Nullable
        @Override
        public Exception getException() {
            return mTask.getException();
        }

        @NonNull
        @Override
        public Task<Void> addOnSuccessListener(@NonNull OnSuccessListener<? super Void> onSuccessListener) {
            return mTask.addOnSuccessListener(onSuccessListener);
        }

        @NonNull
        @Override
        public Task<Void> addOnSuccessListener(@NonNull Executor executor, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
            return mTask.addOnSuccessListener(executor, onSuccessListener);
        }

        @NonNull
        @Override
        public Task<Void> addOnSuccessListener(@NonNull Activity activity, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
            return mTask.addOnSuccessListener(activity, onSuccessListener);
        }

        @NonNull
        @Override
        public Task<Void> addOnFailureListener(@NonNull OnFailureListener onFailureListener) {
            return mTask.addOnFailureListener(onFailureListener);
        }

        @NonNull
        @Override
        public Task<Void> addOnFailureListener(@NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
            return mTask.addOnFailureListener(executor, onFailureListener);
        }

        @NonNull
        @Override
        public Task<Void> addOnFailureListener(@NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
            return mTask.addOnFailureListener(activity, onFailureListener);
        }
    }
}
