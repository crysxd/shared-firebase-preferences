package sharefirebasepreferences.crysxd.de.lib;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.os.AsyncTask.execute;

/**
 * A {@link SharedPreferences} implementation storing the data in the Firebase realtime database.
 * As default, all sharedpreferences are stored under /shared-preferences in your Firebase database.
 * Use {@link #setDatabaseRoot(String)} to modify the root reference of the shared preferences. The root
 * will contain a child for each user under which children for all different preferences will be stored.
 * <p>
 * You can user {@link #getInstance(String)} according to {@link Context#getSharedPreferences(String, int)}.
 * <p>
 * please note that the preferences are not instantly synced with other devices but uploaded when a edit
 * is completed and downloaded when initialised.
 * <p>
 * It is strongly recommended to set {@link FirebaseDatabase#setPersistenceEnabled(boolean)} to true
 * in order to ensure the preferences are functional when the device is offline.
 */
public class SharedFirebasePreferences implements SharedPreferences, ValueEventListener {

    /**
     * The log tag
     */
    private static final String TAG = "FirebasePreferences";

    /**
     * A static map with all singleton instances
     */
    private static Map<String, SharedFirebasePreferences> sInstances = new HashMap<>();

    /**
     * The currently used root
     */
    private static String sRoot = "/shared-preferences";

    /**
     * The listeners
     */
    private List<OnSharedPreferenceChangeListener> mListener = new ArrayList<>();

    /**
     * The root referece for this preferences
     */
    private DatabaseReference mReference;

    /**
     * A recently occured error
     */
    private DatabaseError mError;

    /**
     * A flag to remeber whether we cached the data
     */
    private Map<String, Object> mCache;

    /**
     * The executor to await async requests
     */
    private Executor mExecutor = Executors.newSingleThreadExecutor();

    /**
     * Flag to check whether the cache is currently build
     */
    private boolean mCaching;

    /**
     * The Executor
     */
    private HandlerThread thread = new HandlerThread("firebase-db");


    protected SharedFirebasePreferences(DatabaseReference reference) {
        mReference = reference;
        thread.start();
    }

    public synchronized static SharedFirebasePreferences getInstance(String name) {
        return getInstance(FirebaseDatabase.getInstance(), name);
    }

    public synchronized static SharedFirebasePreferences getInstance(FirebaseDatabase db, String name) {
        if (!sInstances.containsKey(name)) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                throw new IllegalStateException("User must be signed in at Firebase");
            }
            name = name.replace('.', '-').replace('$', '-').replace('#', '-').replace('[', '-').replace(']', '-');
            sInstances.put(name, new SharedFirebasePreferences(db.getReference(sRoot).child(user.getUid()).child(name)));
        }
        return sInstances.get(name);
    }

    @VisibleForTesting
    public static void inject(String name, SharedFirebasePreferences instance) {
        sInstances.put(name, instance);
    }

    public void setDatabaseRoot(String root) {
        sRoot = root;
    }

    private synchronized Object getValue(String key, Object backup) {
        Object hit = this.mCache.get(key);
        return hit != null ? hit : backup;
    }

    /**
     * Loads the data from firebase into the cache
     *
     * @throws RuntimeException if the operation is interrupted or the database read failed
     */
    private synchronized void loadCache() throws RuntimeException {
        try {
            if (mCache == null) {
                if (mCaching) {
                    this.wait();
                } else {
                    mCaching = true;
                    mCache = new HashMap<>();
                    mReference.addValueEventListener(SharedFirebasePreferences.this);
                    mError = null;
                    Log.i(TAG, "Loading preferences into cache");
                    mCaching = false;
                }

                if (mError != null) {
                    throw new RuntimeException(mError.toException());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized Map<String, ?> getAll() {
        loadCache();
        return new HashMap<>(mCache);
    }

    @Nullable
    @Override
    public synchronized String getString(String s, @Nullable String s1) {
        return (String) getValue(s, s1);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String s, @Nullable Set<String> set) {
        return new HashSet<>((List<String>) getValue(s, set));
    }

    @Override
    public synchronized int getInt(String s, int i) {
        return (int) getValue(s, i);
    }

    @Override
    public synchronized long getLong(String s, long l) {
        return (long) getValue(s, l);
    }

    @Override
    public synchronized float getFloat(String s, float v) {
        return (float) getValue(s, v);
    }

    @Override
    public synchronized boolean getBoolean(String s, boolean b) {
        return (boolean) getValue(s, b);
    }

    @Override
    public synchronized boolean contains(String s) {
        loadCache();
        return this.getValue(s, null) != null;
    }

    @Override
    public Editor edit() {
        return new Editor(this);
    }

    @Override
    public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mListener.add(onSharedPreferenceChangeListener);
    }

    @Override
    public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mListener.remove(onSharedPreferenceChangeListener);
    }

    @Override
    public synchronized void onDataChange(DataSnapshot dataSnapshot) {
        mCache.clear();
        for (DataSnapshot d : dataSnapshot.getChildren()) {
            d.getValue();
            mCache.put(d.getKey(), d.getValue());
        }
        this.notifyAll();
    }

    @Override
    public synchronized void onCancelled(DatabaseError databaseError) {
        mError = databaseError;
        this.notifyAll();

    }

    private void dispatchPreferenceChanged(Map<String, Object> old, Map<String, Object> current) {
        // Delete
        for (String key : current.keySet()) {
            if (!old.containsKey(key)) {
                dispatchPreferenceChanged(key, null);
            }
        }

        // Add
        for (String key : old.keySet()) {
            if (!current.containsKey(key)) {
                dispatchPreferenceChanged(key, old.get(key));
            }
        }

        // Update
        for (String key : current.keySet()) {
            if (old.containsKey(key) && !old.get(key).equals(current.get(key))) {
                dispatchPreferenceChanged(key, old.get(key));
            }
        }
    }

    private void dispatchPreferenceChanged(String key, Object o) {
        for (OnSharedPreferenceChangeListener l : mListener) {
            try {
                l.onSharedPreferenceChanged(this, key);
            } catch (Exception e) {
                Log.e(TAG, "Error while notifying listener", e);
            }
        }
    }

    /**
     * A {@link Editor} for {@link SharedFirebasePreferences}
     */
    public static class Editor implements SharedPreferences.Editor {

        /**
         * The cache which is edited
         */
        private Map<String, Object> mCache;

        /**
         * The {@link SharedFirebasePreferences} currently being edited
         */
        private SharedFirebasePreferences mPreferences;

        /**
         * Creates a new instance to edito the given preferences
         *
         * @param preferences the {@link SharedFirebasePreferences} to edit
         */
        Editor(SharedFirebasePreferences preferences) {
            mCache = new HashMap<>(preferences.mCache);
            mPreferences = preferences;
        }

        @Override
        public SharedPreferences.Editor putString(String s, @Nullable String s1) {
            mCache.put(s, s1);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String s, @Nullable Set<String> set) {
            mCache.put(s, set == null ? null : new ArrayList<>(set));
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String s, int i) {
            mCache.put(s, i);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String s, long l) {
            mCache.put(s, l);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String s, float v) {
            mCache.put(s, v);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String s, boolean b) {
            mCache.put(s, b);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String s) {
            mCache.remove(s);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            mCache.clear();
            return this;
        }

        @Override
        public boolean commit() {
            Map<String, Object> beforeChange = new HashMap<>(mPreferences.mCache);
            mPreferences.mReference.updateChildren(mCache);
            mPreferences.dispatchPreferenceChanged(beforeChange, mCache);
            mPreferences.mCache = mCache;

            // Return success
            return true;
        }

        @Override
        public void apply() {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... voids) {
                    commit();
                    return null;

                }
            };
        }
    }
}
