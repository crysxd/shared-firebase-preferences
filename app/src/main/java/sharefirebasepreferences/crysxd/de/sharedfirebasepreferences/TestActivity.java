package sharefirebasepreferences.crysxd.de.sharedfirebasepreferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import sharefirebasepreferences.crysxd.de.lib.SharedFirebasePreferences;
import sharefirebasepreferences.crysxd.de.lib.SharedFirebasePreferencesContextWrapper;


public class TestActivity extends AppCompatActivity implements FirebaseAuth.AuthStateListener {

    private static final String TAG = "activity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test);

        FirebaseApp.initializeApp(this);
        FirebaseAuth.getInstance().addAuthStateListener(this);

        FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInAnonymously:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInAnonymously", task.getException());
                            Toast.makeText(TestActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new SharedFirebasePreferencesContextWrapper(newBase));
    }


    @Override
    public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        if (firebaseAuth.getCurrentUser() != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs instanceof SharedFirebasePreferences) {
                ((SharedFirebasePreferences) prefs).pull().addOnFetchCompleteListener(new SharedFirebasePreferences.OnFetchCompleteListener() {
                    @Override
                    public void onFetchSucceeded(SharedFirebasePreferences preferences) {
                        showView();
                    }

                    @Override
                    public void onFetchFailed(Exception e) {
                        showView();
                        Toast.makeText(TestActivity.this, "Fetch failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void showView() {
        getFragmentManager().beginTransaction().replace(R.id.view, new PreferenceFragment()).commitAllowingStateLoss();
        findViewById(R.id.progessBar).setVisibility(View.GONE);
    }

    public static class PreferenceFragment extends android.preference.PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

        }
    }
}
