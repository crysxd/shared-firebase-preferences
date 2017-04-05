package sharefirebasepreferences.crysxd.de.lib;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

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

/**
 * Created by chrwue on 05/04/2017.
 */
public class SharedFirebasePreferences implements SharedPreferences, ValueEventListener {

    private static final String TAG = "FirebasePreferences";
    private static Map<String, SharedFirebasePreferences> sInstances = new HashMap<>();
    private static String sRoot = "/shared-preferences";
    private Map<String, Object> mCache;
    private List<OnSharedPreferenceChangeListener> mListener;
    private DatabaseReference mReference;
    private DatabaseError mError;

    protected SharedFirebasePreferences(DatabaseReference reference) {
        mReference = reference;
    }

    public synchronized static SharedFirebasePreferences getInstance(String name) {
        if (!sInstances.containsKey(name)) {
            sInstances.put(name, new SharedFirebasePreferences(FirebaseDatabase.getInstance().getReference(sRoot).child(name)));
        }
        return sInstances.get(name);
    }

    public void setDatabaseRoot(String root) {
        sRoot = root;
    }

    @VisibleForTesting
    public void inject(String name, SharedFirebasePreferences instance) {
        sInstances.put(name, instance);
    }

    protected Object getValue(String key, Object backup) {
        Object hit = this.mCache.get(key);
        return hit != null ? hit : backup;
    }

    /**
     * Loads the data from firebase into the cache
     *
     * @throws RuntimeException if the operation is interrupted or the database read failed
     */
    protected synchronized void loadCache() throws RuntimeException {
        try {
            if (mCache == null) {
                Log.i(TAG, "Loading preferences into cache");
                mCache = new HashMap<>();
                mReference.addListenerForSingleValueEvent(this);
                mError = null;
                this.wait();

                if (mError != null) {
                    throw new RuntimeException(mError.toException());
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, ?> getAll() {
        loadCache();
        return new HashMap<>(mCache);
    }

    @Nullable
    @Override
    public String getString(String s, @Nullable String s1) {
        return (String) getValue(s, s1);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String s, @Nullable Set<String> set) {
        return new HashSet<>((List<String>) getValue(s, set));
    }

    @Override
    public int getInt(String s, int i) {
        return (int) getValue(s, i);
    }

    @Override
    public long getLong(String s, long l) {
        return (long) getValue(s, l);
    }

    @Override
    public float getFloat(String s, float v) {
        return (float) getValue(s, v);
    }

    @Override
    public boolean getBoolean(String s, boolean b) {
        return (boolean) getValue(s, b);
    }

    @Override
    public boolean contains(String s) {
        return this.mCache.containsKey(s);
    }

    @Override
    public Editor edit() {
        return new Editor(this);
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mListener.add(onSharedPreferenceChangeListener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        mListener.remove(onSharedPreferenceChangeListener);
    }

    @Override
    public synchronized void onDataChange(DataSnapshot dataSnapshot) {
        mCache.clear();
        for (DataSnapshot d : dataSnapshot.getChildren()) {
            d.getValue();
            mCache.put(d.getKey(), d.getValue());
        }
        this.notify();
    }

    @Override
    public synchronized void onCancelled(DatabaseError databaseError) {
        mError = databaseError;
        this.notify();
    }

    protected void dispatchPreferenceChanged(String key, Object o) {
        mCache.put(key, o);

        for (OnSharedPreferenceChangeListener l : mListener) {
            try {
                l.onSharedPreferenceChanged(this, key);
            } catch (Exception e) {
                Log.e(TAG, "Error while notifying listener", e);
            }
        }
    }

    public static class Editor implements SharedPreferences.Editor {

        private Map<String, Object> mBackup;

        private SharedFirebasePreferences mPreferences;

        protected Editor(SharedFirebasePreferences preferences) {
            mBackup = new HashMap<>(preferences.mCache);
            mPreferences = preferences;
        }

        @Override
        public SharedPreferences.Editor putString(String s, @Nullable String s1) {
            mBackup.put(s, s1);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String s, @Nullable Set<String> set) {
            mBackup.put(s, set == null ? null : new ArrayList<>(set));
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String s, int i) {
            mBackup.put(s, i);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String s, long l) {
            mBackup.put(s, l);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String s, float v) {
            mBackup.put(s, v);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String s, boolean b) {
            mBackup.put(s, b);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String s) {
            mBackup.remove(s);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            mBackup.clear();
            return this;
        }

        @Override
        public boolean commit() {
            HashMap<String, Object> currentState = new HashMap<>(mPreferences.mCache);

            // Delete
            for (String key : currentState.keySet()) {
                if (!mBackup.containsKey(key)) {
                    mPreferences.mReference.child(key).removeValue();
                    mPreferences.dispatchPreferenceChanged(key, null);
                }
            }

            // Add
            for (String key : mBackup.keySet()) {
                if (!currentState.containsKey(key)) {
                    mPreferences.mReference.child(key).removeValue();
                    mPreferences.dispatchPreferenceChanged(key, mBackup.get(key));
                }
            }

            // Update
            for (String key : currentState.keySet()) {
                if (mBackup.containsKey(key) && !mBackup.get(key).equals(currentState.get(key))) {
                    mPreferences.mReference.child(key).removeValue();
                    mPreferences.dispatchPreferenceChanged(key, mBackup.get(key));
                }
            }

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
