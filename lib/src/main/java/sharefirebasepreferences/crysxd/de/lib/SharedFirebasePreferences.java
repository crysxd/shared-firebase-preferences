package sharefirebasepreferences.crysxd.de.lib;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by chrwue on 05/04/2017.
 */
public class SharedFirebasePreferences implements SharedPreferences {

    private static final String TAG = "FirebasePreferences";
    private static Map<String, SharedFirebasePreferences> sInstances = new HashMap<>();
    private Map<String, ?> mCache;
    private List<OnSharedPreferenceChangeListener> mListener;

    protected SharedFirebasePreferences(String name) {

    }

    public synchronized static SharedFirebasePreferences getInstance(String name) {
        if (!sInstances.containsKey(name)) {
            sInstances.put(name, new SharedFirebasePreferences(name));
        }
        return sInstances.get(name);
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
     */
    protected void loadCache() {
        if (mCache == null) {
            Log.i(TAG, "Loading preferences into cache");
            mCache = new HashMap<>();
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
        return (Set<String>) getValue(s, set);
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

    public static class Editor implements SharedPreferences.Editor {

        private Map<String, Object> mBackup;

        protected Editor(SharedFirebasePreferences preferences) {
            mBackup = new HashMap<>(preferences.mCache);
        }

        @Override
        public SharedPreferences.Editor putString(String s, @Nullable String s1) {
            mBackup.put(s, s1);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String s, @Nullable Set<String> set) {
            mBackup.put(s, set);
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
            return false;
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
