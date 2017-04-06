package sharefirebasepreferences.crysxd.de.lib;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

/**
 * A {@link ContextWrapper} which reroutes the {@link #getSharedPreferences(String, int)} method
 * to {@link SharedFirebasePreferences}
 */
public class SharedFirebasePreferencesContextWrapper extends ContextWrapper {

    /**
     * Creates a new instance
     *
     * @param base the base context
     */
    public SharedFirebasePreferencesContextWrapper(Context base) {
        super(base);
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return SharedFirebasePreferences.getInstance(name);
    }
}