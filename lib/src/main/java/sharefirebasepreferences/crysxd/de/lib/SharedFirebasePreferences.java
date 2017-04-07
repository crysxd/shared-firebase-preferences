package sharefirebasepreferences.crysxd.de.lib;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A {@link SharedPreferences} implementation which syncs all data with firebase. Use {@link #getInstance(Context, String, int)}
 * to receive a instance
 */
@SuppressWarnings("WeakerAccess")
public class SharedFirebasePreferences implements SharedPreferences {

    /**
     * The log tag
     */
    private static final String TAG = "SharedFirebasePrefs";

    /**
     * The instances
     */
    private static Map<String, SharedFirebasePreferences> sInstances = new HashMap<>();

    /**
     * The wrapped {@link SharedPreferences}
     */
    private SharedPreferences mCache;

    /**
     * The {@link DatabaseReference} which is used for storing
     */
    private DatabaseReference mRoot;

    /**
     * Creates a new instance
     *
     * @param cache the wrapped {@link SharedPreferences}
     * @param root  the {@link DatabaseReference} used for storing
     */
    protected SharedFirebasePreferences(SharedPreferences cache, DatabaseReference root) {
        mCache = cache;
        mRoot = root;
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
        // Check if any user is signed in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No user signed in with firebase");
        }

        // Get the instance
        return getInstance(con, name, mode, FirebaseDatabase.getInstance()
                .getReference(sanatizeString("shared_prefs"))
                .child(sanatizeString(user.getUid()))
                .child(sanatizeString(name)));

    }

    /**
     * Removes all character forbidden for firebase paths
     *
     * @param s the string to sanatize
     * @return the sanatized string
     */
    private static String sanatizeString(String s) {
        return s.replace('.', '-').replace('#', '-').replace('$', '-').replace('[', '-').replace(']', '-');
    }

    /**
     * Returns a instance for the given name
     *
     * @param con  a {@link Context}
     * @param name the preferences names. If the name already exists as local preferences, the data will be pushed to Firebase
     * @param mode the mode
     * @param root the {@link DatabaseReference} used to store the preferences
     * @return the instance
     * @see Context#MODE_PRIVATE
     */
    public synchronized static SharedFirebasePreferences getInstance(Context con, String name, int mode, DatabaseReference root) {
        // Check if we already have a instance, create new one if not
        if (!sInstances.containsKey(name)) {
            sInstances.put(name, new SharedFirebasePreferences(con.getSharedPreferences(name, mode), root));
        }

        // Return the singleton
        return sInstances.get(name);

    }

    /**
     * Fetches the latest data from Firebase
     *
     * @return the {@link FetchTask}
     */
    public FetchTask fetch() {
        return new FetchTask(this).addOnFetchCompleteListener(new OnFetchCompleteListener() {
            @Override
            public void onFetchSucceeded(SharedFirebasePreferences preferences) {
                Log.i(TAG, "Fetch of " + getRoot().toString() + " succeeded");
            }

            @Override
            public void onFetchFailed(Exception e) {
                Log.e(TAG, "Fetch of " + getRoot().toString() + " failed", e);
            }
        });
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
     * A listener to get notified about fetch results
     */
    public interface OnFetchCompleteListener {

        /**
         * Called when the fetch was successful
         *
         * @param preferences the updated {@link SharedFirebasePreferences}
         */
        void onFetchSucceeded(SharedFirebasePreferences preferences);

        /**
         * Called when the fetch failed
         *
         * @param e the occured {@link Exception}
         */
        void onFetchFailed(Exception e);

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
     * A task fetching the latest values from firebase
     */
    public static class FetchTask implements ValueEventListener {

        /**
         * The {@link SharedFirebasePreferences} which should be fetched from Firebase
         */
        private SharedFirebasePreferences mPreferences;

        /**
         * The listeners
         */
        private List<OnFetchCompleteListener> mListener = new ArrayList<>();

        /**
         * Creates a new instance
         *
         * @param preferences the {@link SharedFirebasePreferences} which should be fetched from Firebase
         */
        public FetchTask(SharedFirebasePreferences preferences) {
            mPreferences = preferences;
            mPreferences.getRoot().addListenerForSingleValueEvent(this);

        }

        /**
         * Adds a {@link OnFetchCompleteListener} to get informed when the fetch is completed
         *
         * @param listener the {@link OnFetchCompleteListener}
         * @return this instance
         */
        public FetchTask addOnFetchCompleteListener(@NonNull OnFetchCompleteListener listener) {
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
                    } else if (v instanceof Set) {
                        //noinspection unchecked
                        e.putStringSet(k, (Set<String>) v);
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
         * Dispatches the {@link OnFetchCompleteListener#onFetchFailed(Exception)}
         * event for all listeners
         */
        private void dispatchFetchFailed(Exception e) {
            for (OnFetchCompleteListener l : mListener) {
                try {
                    l.onFetchFailed(e);

                } catch (Exception e2) {
                    Log.e(TAG, "Error while dispatching onFetchFailed() event", e);

                }
            }
        }

        /**
         * Dispatches the {@link OnFetchCompleteListener#onFetchSucceeded(SharedFirebasePreferences)}
         * event for all listeners
         */
        private void dispatchFetchSucceeded() {
            for (OnFetchCompleteListener l : mListener) {
                try {
                    l.onFetchSucceeded(mPreferences);

                } catch (Exception e) {
                    Log.e(TAG, "Error while dispatching onFetchSucceeded() event", e);

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
            mTask = preferences.getRoot().updateChildren(new HashMap<>(preferences.getAll()));
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
